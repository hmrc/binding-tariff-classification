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

import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import org.mongodb.scala.model.Indexes.ascending
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.*
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import util.CaseData.{createBasicBTIApplication, createLiabilityOrder}

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

      await(viewRepo.startListening())

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

    "syncSingleCase should clear old rows and insert new keyword rows when a case is updated" in {
      await(caseRepository.insert(btiCase))
      await(viewRepo.startListening())

      val updatedBtiCase = btiCase.copy(keywords = Set("phone", "apple", "mobile"))
      await(caseRepository.update(updatedBtiCase, upsert = false))

      val rowsBefore = await(viewRepo.collection.find().toFuture())
      rowsBefore.map(_.keyword) should contain allOf ("phone", "tech")

      await(viewRepo.startListening())

      val rowsAfter     = await(viewRepo.collection.find().toFuture())
      val keywordsAfter = rowsAfter.map(_.keyword)

      keywordsAfter should contain("apple")
      keywordsAfter should contain("mobile")
      keywordsAfter should contain("phone")
      keywordsAfter shouldNot contain("tech")
    }
  }
}
