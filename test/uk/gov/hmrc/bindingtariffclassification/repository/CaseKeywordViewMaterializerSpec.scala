/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.bindingtariffclassification.repository

import org.bson.{BsonDocument, BsonObjectId}
import org.bson.types.ObjectId
import org.mockito.Mockito.when
import org.mongodb.scala.model.Filters.{equal => mongoEqual}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.changestream.ChangeStreamDocument
import com.mongodb.client.model.changestream.OperationType
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers._
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.*
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import util.CaseData.{createBasicBTIApplication, createCorrespondenceApplication, createLiabilityOrder, createMiscApplication}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class CaseKeywordViewMaterializerSpec
    extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with MockitoSugar
    with DefaultPlayMongoRepositorySupport[CaseKeywordViewRow] {

  private val config = mock[AppConfig]
  private val caseRepository =
    new CaseMongoRepository(config, mongoComponent, new SearchMapper(config), new UpdateMapper)
  private val viewRepo = new CaseKeywordViewMaterializer(mongoComponent, config, caseRepository)

  override protected val repository: PlayMongoRepository[CaseKeywordViewRow] = viewRepo
  override protected val checkTtlIndex                                       = false

  private val btiCase: Case = Case(
    reference = "0000001",
    status = CaseStatus.OPEN,
    createdDate = Instant.now(),
    queueId = Some("queue_1"),
    assignee = Some(Operator("001", Some("John Doe"))),
    application = createBasicBTIApplication.copy(goodName = "Test Smartphone"),
    decision = None,
    attachments = Seq.empty,
    keywords = Set("phone", "tech")
  )

  private val liabilityCase: Case = Case(
    reference = "0000002",
    status = CaseStatus.OPEN,
    createdDate = Instant.now(),
    queueId = Some("queue_2"),
    assignee = Some(Operator("002", Some("Jane Doe"))),
    application = createLiabilityOrder.copy(goodName = Some("Dryer"), status = LiabilityStatus.LIVE),
    decision = None,
    attachments = Seq.empty,
    keywords = Set("dryer")
  )

  val correspondenceCase = Case(
    reference = "0000003",
    status = CaseStatus.OPEN,
    createdDate = Instant.now(),
    queueId = Some("queue_3"),
    assignee = Some(Operator("003", Some("Bob"))),
    application = createCorrespondenceApplication,
    decision = None,
    attachments = Seq.empty,
    keywords = Set("correspondence")
  )

  val miscCase = Case(
    reference = "0000004",
    status = CaseStatus.OPEN,
    createdDate = Instant.now(),
    queueId = Some("queue_4"),
    assignee = Some(Operator("004", Some("Alice"))),
    application = createMiscApplication,
    decision = None,
    attachments = Seq.empty,
    keywords = Set("misc")
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    deleteAll()
    await(caseRepository.collection.drop().toFuture())

    await(caseRepository.ensureIndexes())

    await(
      mongoComponent.database
        .getCollection("keywords")
        .createIndex(ascending("approved"))
        .toFuture()
    )
  }

  "CaseKeywordViewUpdater" should {

    "rebuildViewFromScratch should empty the view and completely rebuild it from caseRepository" in {
      await(caseRepository.insert(btiCase))
      await(caseRepository.insert(liabilityCase))

      await(viewRepo.rebuildViewFromScratch())

      val viewRows = await(viewRepo.collection.find().toFuture())
      viewRows.size shouldBe 3

      val phoneRow = viewRows.find(_.keyword == "phone").get
      phoneRow.caseId    shouldBe "0000001"
      phoneRow.goodsName shouldBe Some("Test Smartphone")
      phoneRow.caseType  shouldBe Some("BTI")
      phoneRow.assignee  shouldBe Some("John Doe")

      val dryerRow = viewRows.find(_.keyword == "dryer").get
      dryerRow.caseId          shouldBe "0000002"
      dryerRow.goodsName       shouldBe Some("Dryer")
      dryerRow.caseType        shouldBe Some("LIABILITY_ORDER")
      dryerRow.liabilityStatus shouldBe Some("LIVE")
    }

    "rebuildViewFromScratch should create rows for CorrespondenceApplication" in {

      await(caseRepository.insert(correspondenceCase))
      await(viewRepo.rebuildViewFromScratch())

      val rows = await(viewRepo.collection.find().toFuture())

      rows should have size 1

      val row = rows.head
      row.keyword         shouldBe "correspondence"
      row.goodsName       shouldBe None
      row.liabilityStatus shouldBe None
    }

    "rebuildViewFromScratch should create rows for MiscApplication" in {

      await(caseRepository.insert(miscCase))
      await(viewRepo.rebuildViewFromScratch())

      val rows = await(viewRepo.collection.find().toFuture())

      rows should have size 1

      val row = rows.head
      row.keyword         shouldBe "misc"
      row.goodsName       shouldBe None
      row.liabilityStatus shouldBe None
    }

    "syncSingleCase should clear old rows and insert new keyword rows when a case is updated" in {
      await(caseRepository.insert(btiCase))
      await(viewRepo.rebuildViewFromScratch())
      await(viewRepo.ensureIndexes())

      val updatedBtiCase = btiCase.copy(keywords = Set("phone", "apple", "mobile"))
      await(caseRepository.update(updatedBtiCase, upsert = false))

      val rowsBefore = await(viewRepo.collection.find().toFuture())
      rowsBefore.map(_.keyword) should contain allOf ("phone", "tech")

      await(viewRepo.rebuildViewFromScratch())

      val rowsAfter     = await(viewRepo.collection.find().toFuture())
      val keywordsAfter = rowsAfter.map(_.keyword)

      keywordsAfter should contain("apple")
      keywordsAfter should contain("mobile")
      keywordsAfter should contain("phone")
      keywordsAfter shouldNot contain("tech")
    }

    "syncSingleCase should replace existing rows for a case" in {

      await(caseRepository.insert(btiCase))
      await(viewRepo.rebuildViewFromScratch())
      await(viewRepo.ensureIndexes())

      val updated =
        btiCase.copy(keywords = Set("apple"))

      await(viewRepo.syncSingleCase(updated))

      val rows = await(viewRepo.collection.find().toFuture())

      rows.map(_.keyword) shouldBe Seq("apple")
    }

    "findRows should return rows matching the filter" in {
      await(caseRepository.insert(btiCase))
      await(caseRepository.insert(liabilityCase))

      await(viewRepo.rebuildViewFromScratch())

      val rows =
        await(viewRepo.findRows(mongoEqual("keyword", "phone"), 0, 10))

      rows                should have size 1
      rows.head.keyword shouldBe "phone"
    }

    "countRows should count rows matching the filter" in {
      await(caseRepository.insert(btiCase))
      await(caseRepository.insert(liabilityCase))

      await(viewRepo.rebuildViewFromScratch())

      val count =
        await(viewRepo.countRows(mongoEqual("keyword", "phone")))

      count shouldBe 1
    }
  }
}
