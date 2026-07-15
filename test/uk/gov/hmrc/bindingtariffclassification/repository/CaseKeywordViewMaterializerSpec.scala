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

import com.mongodb.client.model.changestream.ChangeStreamDocument
import org.bson.types.ObjectId
import org.bson.{BsonDocument, BsonObjectId}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{atLeastOnce, verify, when, inOrder as mockitoInOrder}
import org.mongodb.scala.model.Filters.equal as mongoEqual
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import org.scalatest.matchers.should.Matchers.*
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.*
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import util.CaseData.{createBasicBTIApplication, createCorrespondenceApplication, createLiabilityOrder, createMiscApplication}

import java.time.{Instant, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CaseKeywordViewMaterializerSpec
    extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with MockitoSugar
    with DefaultPlayMongoRepositorySupport[CaseKeywordViewRow] {

  private val config = mock[AppConfig]
  private val caseRepository =
    new CaseMongoRepository(config, mongoComponent, new SearchMapper(config), new UpdateMapper)
  private val migrationLockRepository = mock[MigrationLockRepository]

  private val viewRepo =
    new CaseKeywordViewMaterializer(
      mongoComponent,
      config,
      caseRepository,
      migrationLockRepository
    )

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

    "startListening should remove a stale lock and continue when lock cannot be reacquired" in {
      val staleLock =
        JobRunEvent(
          "case-keyword-view-rebuild",
          ZonedDateTime.now().minusDays(2)
        )

      when(migrationLockRepository.findOne("case-keyword-view-rebuild"))
        .thenReturn(Future.successful(Some(staleLock)))

      when(migrationLockRepository.delete(staleLock))
        .thenReturn(Future.successful(()))

      when(migrationLockRepository.lock(any[JobRunEvent]))
        .thenReturn(Future.successful(false))

      await(viewRepo.startListening())

      verify(migrationLockRepository).delete(staleLock)
      verify(migrationLockRepository).lock(any[JobRunEvent])
    }

    "startListening should delete a stale lock before acquiring a new one" in {
      val staleLock =
        JobRunEvent(
          "case-keyword-view-rebuild",
          ZonedDateTime.now().minusDays(2)
        )

      when(migrationLockRepository.findOne("case-keyword-view-rebuild"))
        .thenReturn(Future.successful(Some(staleLock)))

      when(migrationLockRepository.delete(staleLock))
        .thenReturn(Future.successful(()))

      when(migrationLockRepository.lock(any[JobRunEvent]))
        .thenReturn(Future.successful(true))

      await(viewRepo.startListening())

      val inOrderVerifier = mockitoInOrder(migrationLockRepository)
      inOrderVerifier.verify(migrationLockRepository).delete(staleLock)
      inOrderVerifier.verify(migrationLockRepository).lock(any[JobRunEvent])

      verify(migrationLockRepository, atLeastOnce())
        .delete(any[JobRunEvent])
    }

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

    "rebuildViewFromScratch should ignore cases without keywords" in {

      val noKeywordCase =
        btiCase.copy(
          reference = "0000003",
          keywords = Set.empty
        )

      await(caseRepository.insert(noKeywordCase))

      await(viewRepo.rebuildViewFromScratch())

      val rows =
        await(viewRepo.collection.find().toFuture())

      rows shouldBe empty
    }

    "syncSingleCase should upsert keyword rows when a case is updated" in {

      await(caseRepository.insert(btiCase))
      await(viewRepo.rebuildViewFromScratch())

      val updatedBtiCase =
        btiCase.copy(
          keywords = Set("phone", "apple", "mobile")
        )

      await(viewRepo.syncSingleCase(updatedBtiCase))

      val rows =
        await(viewRepo.collection.find().toFuture())

      val keywords =
        rows.map(_.keyword)

      keywords should contain("phone")
      keywords should contain("apple")
      keywords should contain("mobile")
    }

    "syncSingleCase should update existing rows without creating duplicates" in {

      await(caseRepository.insert(btiCase))

      await(viewRepo.rebuildViewFromScratch())

      val updated =
        btiCase.copy(
          keywords = Set("phone", "tech")
        )

      await(viewRepo.syncSingleCase(updated))

      val rows =
        await(
          viewRepo.collection
            .find(mongoEqual("caseId", btiCase.reference))
            .toFuture()
        )

      rows should have size 2

      rows.map(_.keyword) should contain allOf (
        "phone",
        "tech"
      )
    }

    "syncSingleCase should remove existing rows when case has no keywords" in {

      await(caseRepository.insert(btiCase))
      await(viewRepo.rebuildViewFromScratch())

      val noKeywordCase =
        btiCase.copy(
          keywords = Set.empty
        )

      await(viewRepo.syncSingleCase(noKeywordCase))

      val rows =
        await(
          viewRepo.collection
            .find(mongoEqual("caseId", btiCase.reference))
            .toFuture()
        )

      rows shouldBe empty
    }

    "syncSingleCase should not create duplicate rows when called twice" in {

      await(viewRepo.syncSingleCase(btiCase))
      await(viewRepo.syncSingleCase(btiCase))

      val rows =
        await(
          viewRepo.collection
            .find(mongoEqual("caseId", btiCase.reference))
            .toFuture()
        )

      rows should have size 2
    }

    "extractCaseId should return caseId" in {
      val objectId = new ObjectId()
      val docKey   = new BsonDocument("_id", new BsonObjectId(objectId))

      val result = viewRepo.extractCaseId(Some(docKey))

      result shouldBe Some(objectId.toString)
    }

    "extractCaseId should return None when empty" in {
      val result = viewRepo.extractCaseId(None)

      result shouldBe None
    }

    "resolveCase should return fullDocument when present" in {
      val fullDoc = btiCase
      val change = classOf[ChangeStreamDocument[Case]].getConstructors.head
        .newInstance(
          null,
          null,
          null,
          null,
          null,
          fullDoc,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null
        )
        .asInstanceOf[ChangeStreamDocument[Case]]

      val result = await(viewRepo.resolveCase(change, Some("123")))

      result shouldBe Some(fullDoc)
    }

    "resolveCase should return None when everything missing" in {
      val change = classOf[ChangeStreamDocument[Case]].getConstructors.head
        .newInstance(
          null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
        )
        .asInstanceOf[ChangeStreamDocument[Case]]

      val result = await(viewRepo.resolveCase(change, None))

      result shouldBe None
    }

    "applyChange should delete rows when DELETE event" in {
      await(caseRepository.insert(btiCase))
      await(viewRepo.rebuildViewFromScratch())

      val result =
        viewRepo.applyChange(
          "DELETE",
          Some(btiCase.reference),
          None
        )

      await(result)

      val rows = await(viewRepo.collection.find().toFuture())
      rows shouldBe empty
    }

    "applyChange should sync case on INSERT event" in {
      val updated = btiCase.copy(keywords = Set("new-keyword"))

      val result =
        viewRepo.applyChange(
          "INSERT",
          Some(updated.reference),
          Some(updated)
        )

      await(result)

      val rows = await(viewRepo.collection.find().toFuture())
      rows.map(_.keyword) should contain("new-keyword")
    }

    "applyChange should do nothing when caseOpt is missing" in {
      await(caseRepository.insert(btiCase))
      await(viewRepo.rebuildViewFromScratch())

      val result =
        viewRepo.applyChange(
          "UPDATE",
          Some("123"),
          None
        )

      await(result)
      succeed
    }

    "applyChange should ignore unknown operation types" in {
      val result =
        viewRepo.applyChange(
          "RANDOM_EVENT",
          Some("123"),
          None
        )

      await(result)
      succeed
    }

    "applyChange should do nothing on DELETE when caseId is missing" in {
      val result =
        viewRepo.applyChange(
          "DELETE",
          None,
          None
        )

      await(result)
      succeed
    }

    "applyChange should sync case on REPLACE event" in {
      val updated =
        btiCase.copy(
          keywords = Set("replacement-keyword")
        )

      await(
        viewRepo.applyChange(
          "REPLACE",
          Some(updated.reference),
          Some(updated)
        )
      )

      val rows =
        await(viewRepo.collection.find().toFuture())

      rows.map(_.keyword) should contain("replacement-keyword")
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

    "findRows should respect skip and limit" in {
      await(caseRepository.insert(btiCase))
      await(caseRepository.insert(liabilityCase))

      await(viewRepo.rebuildViewFromScratch())

      val rows =
        await(
          viewRepo.findRows(
            mongoEqual("caseId", btiCase.reference),
            1,
            1
          )
        )

      rows should have size 1
    }

    "findRows should respect limit" in {
      await(caseRepository.insert(btiCase))
      await(caseRepository.insert(liabilityCase))

      await(viewRepo.rebuildViewFromScratch())

      val rows =
        await(
          viewRepo.findRows(
            org.mongodb.scala.model.Filters.empty(),
            0,
            1
          )
        )

      rows should have size 1
    }

    "countRows should count rows matching the filter" in {
      await(caseRepository.insert(btiCase))
      await(caseRepository.insert(liabilityCase))

      await(viewRepo.rebuildViewFromScratch())

      val count =
        await(viewRepo.countRows(mongoEqual("keyword", "phone")))

      count shouldBe 1
    }

    "countRows should return zero when no rows match the filter" in {
      await(caseRepository.insert(btiCase))
      await(caseRepository.insert(liabilityCase))

      await(viewRepo.rebuildViewFromScratch())

      val count =
        await(
          viewRepo.countRows(
            mongoEqual("keyword", "does-not-exist")
          )
        )

      count shouldBe 0
    }
  }
}
