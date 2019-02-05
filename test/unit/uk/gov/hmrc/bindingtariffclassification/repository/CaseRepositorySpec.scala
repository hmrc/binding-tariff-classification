/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time.{Instant, LocalDate, ZoneOffset}

import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.libs.json.{JsObject, Json}
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.{Cursor, DB}
import reactivemongo.bson._
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatCase
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.search.{Filter, Search, Sort}
import uk.gov.hmrc.bindingtariffclassification.model.sort.{SortDirection, SortField}
import uk.gov.hmrc.mongo.MongoSpecSupport
import util.CaseData._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CaseRepositorySpec extends BaseMongoIndexSpec
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with MongoSpecSupport
  with Eventually
  with MockitoSugar {
  self =>

  private val mongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private val repository = createMongoRepo

  private def createMongoRepo = {
    new CaseMongoRepository(mongoDbProvider, new SearchMapper)
  }

  private val case1: Case = createCase()
  private val case2: Case = createCase()

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    await(repository.drop)
  }

  private def collectionSize: Int = {
    await(repository.collection.count())
  }

  "deleteAll()" should {

    "clear the collection" in {
      val size = collectionSize

      store(case1, case2)
      collectionSize shouldBe 2 + size

      await(repository.deleteAll()) shouldBe ((): Unit)
      collectionSize shouldBe size
    }

  }

  "insert" should {

    "insert a new document in the collection" in {
      val size = collectionSize

      await(repository.insert(case1)) shouldBe case1
      collectionSize shouldBe 1 + size
      await(repository.collection.find(selectorByReference(case1)).one[Case]) shouldBe Some(case1)
    }

    "fail to insert an existing document in the collection" in {
      await(repository.insert(case1)) shouldBe case1
      val size = collectionSize

      val caught = intercept[DatabaseException] {
        await(repository.insert(case1))
      }
      caught.code shouldBe Some(11000)

      collectionSize shouldBe size
    }

  }

  "update" should {

    "modify an existing document in the collection" in {
      await(repository.insert(case1)) shouldBe case1
      val size = collectionSize

      val updated: Case = case1.copy(application = createBasicBTIApplication, status = CaseStatus.CANCELLED)
      await(repository.update(updated, upsert = false)) shouldBe Some(updated)
      collectionSize shouldBe size

      await(repository.collection.find(selectorByReference(updated)).one[Case]) shouldBe Some(updated)
    }

    "do nothing when trying to update a non existing document in the collection" in {
      val size = collectionSize

      await(repository.update(case1, upsert = false)) shouldBe None
      collectionSize shouldBe size
    }

    "create a new existing document in the collection if upsert permitted" in {
      val size = collectionSize

      await(repository.update(case1, upsert = true)) shouldBe Some(case1)
      collectionSize shouldBe size + 1
    }
  }

  // TODO: test all possible combinations
  // TODO: the test scenarios titles need to be written and grouped properly

  "get without params" should {

    "retrieve all cases from the collection unsorted" in {

      val search = Search(Filter(), None)

      await(repository.insert(case1))
      await(repository.insert(case2))
      collectionSize shouldBe 2

      await(repository.get(search)) shouldBe Seq(case1, case2)
    }

    "return all cases from the collection sorted ascending" in {
      val search = Search(Filter(), Some(Sort(SortField.DAYS_ELAPSED, SortDirection.ASCENDING)))

      val oldCase = case1.copy(daysElapsed = 1)
      val newCase = case2.copy(daysElapsed = 0)
      await(repository.insert(oldCase))
      await(repository.insert(newCase))

      collectionSize shouldBe 2

      await(repository.get(search)) shouldBe Seq(newCase, oldCase)
    }

    "return all cases from the collection sorted descending" in {

      val search = Search(Filter(), Some(Sort(SortField.DAYS_ELAPSED, SortDirection.DESCENDING)))

      val oldCase = case1.copy(daysElapsed = 1)
      val newCase = case2.copy(daysElapsed = 0)
      await(repository.insert(oldCase))
      await(repository.insert(newCase))

      collectionSize shouldBe 2

      await(repository.get(search)) shouldBe Seq(oldCase, newCase)
    }

    "return an empty sequence when there are no cases in the collection" in {
      val search = Search(Filter(), None)
      await(repository.get(search)) shouldBe Seq.empty[Case]
    }
  }

  "get filtering by queueId" should {

    val queueIdX = Some("queue_x")
    val queueIdY = Some("queue_y")
    val unknownQueueId = Some("unknown_queue_id")

    val caseWithEmptyQueue = createCase()
    val caseWithQueueX1 = createCase().copy(queueId = queueIdX)
    val caseWithQueueX2 = createCase().copy(queueId = queueIdX)
    val caseWithQueueY = createCase().copy(queueId = queueIdY)

    "get by filtering on queueId with no matches should return an empty sequence" in {
      val search = Search(Filter(queueId = unknownQueueId), None)

      store(caseWithEmptyQueue, caseWithQueueX1)
      await(repository.get(search)) shouldBe Seq.empty
    }

    "get by filtering on queueId with one match should return the expected document" in {
      val search = Search(Filter(queueId = queueIdX), None)
      store(caseWithEmptyQueue, caseWithQueueX1, caseWithQueueY)
      await(repository.get(search)) shouldBe Seq(caseWithQueueX1)
    }

    "get by filtering on queueId with two matches should return the expected documents" in {
      val search = Search(Filter(queueId = queueIdX), None)

      store(caseWithEmptyQueue, caseWithQueueX1, caseWithQueueX2, caseWithQueueY)
      await(repository.get(search)) shouldBe Seq(caseWithQueueX1, caseWithQueueX2)
    }

  }

  "get filtering by minDecisionDate" should {

    val futureDate = LocalDate.of(3000,1,1).atStartOfDay().toInstant(ZoneOffset.UTC)
    val pastDate = LocalDate.of(1970,1,1).atStartOfDay().toInstant(ZoneOffset.UTC)

    val decisionExpired = createDecision(effectiveEndDate = Some(pastDate))
    val decisionFuture = createDecision(effectiveEndDate = Some(futureDate))
    val caseWithExpiredDecision = createCase(decision = Some(decisionExpired))
    val caseWithFutureDecision = createCase(decision = Some(decisionFuture))

    "no match should return an empty sequence" in {
      val search = Search(Filter(minDecisionEnd = Some(Instant.now())), None)
      store(caseWithExpiredDecision)
      await(repository.get(search)) shouldBe Seq.empty
    }

    "match should return the expected document" in {
      val search = Search(Filter(minDecisionEnd = Some(Instant.now())), None)
      store(caseWithExpiredDecision, caseWithFutureDecision)
      await(repository.get(search)) shouldBe Seq(caseWithFutureDecision)
    }

  }

  "get filtering by assigneeId" should {

    val assigneeX = Operator("assignee_x")
    val assigneeY = Operator("assignee_y")
    val unknownAssignee = Operator("unknown_assignee_id")

    val caseWithEmptyAssignee = createCase()
    val caseWithAssigneeX1 = createCase().copy(assignee = Some(assigneeX))
    val caseWithAssigneeX2 = createCase().copy(assignee = Some(assigneeX))
    val caseWithAssigneeY1 = createCase().copy(assignee = Some(assigneeY))

    "get by filtering on assignee with no matches should return an empty sequence" in {
      val search = Search(Filter(assigneeId = Some(unknownAssignee.id)), None)
      store(caseWithEmptyAssignee, caseWithAssigneeX1)
      await(repository.get(search)) shouldBe Seq.empty
    }

    "get by filtering on assignee with one match should return the expected document" in {
      val search = Search(Filter(assigneeId = Some(assigneeX.id)), None)
      store(caseWithEmptyAssignee, caseWithAssigneeX1, caseWithAssigneeY1)
      await(repository.get(search)) shouldBe Seq(caseWithAssigneeX1)
    }

    "get by filtering on assignee with two matches should return the expected documents" in {
      val search = Search(Filter(assigneeId = Some(assigneeX.id)), None)
      store(caseWithEmptyAssignee, caseWithAssigneeX1, caseWithAssigneeX2, caseWithAssigneeY1)
      await(repository.get(search)) shouldBe Seq(caseWithAssigneeX1, caseWithAssigneeX2)
    }

  }

  "get filtering by status" should {

    val statusX = CaseStatus.NEW
    val statusY = CaseStatus.OPEN

    val caseWithStatusX1 = createCase().copy(status = statusX)
    val caseWithStatusX2 = createCase().copy(status = statusX)
    val caseWithStatusY1 = createCase().copy(status = statusY)

    "get by filtering on status with no matches should return an empty sequence" in {
      val search = Search(Filter(statuses = Some(Set(CaseStatus.DRAFT))), None)
      store(caseWithStatusX1)
      await(repository.get(search)) shouldBe Seq.empty
    }

    "get by filtering on status with one match should return the expected document" in {
      val search = Search(Filter(statuses = Some(Set(CaseStatus.NEW))), None)
      store(caseWithStatusX1, caseWithStatusY1)
      await(repository.get(search)) shouldBe Seq(caseWithStatusX1)
    }

    "get by filtering on status with two matches should return the expected documents" in {
      val search = Search(Filter(statuses = Some(Set(CaseStatus.NEW))), None)
      store(caseWithStatusX1, caseWithStatusX2, caseWithStatusY1)
      await(repository.get(search)) shouldBe Seq(caseWithStatusX1, caseWithStatusX2)
    }

    "get by filtering on statuses with multiple matches should return the expected documents" in {
      val search = Search(Filter(statuses = Some(Set(CaseStatus.NEW, CaseStatus.OPEN))), None)
      store(caseWithStatusX1, caseWithStatusX2, caseWithStatusY1)
      await(repository.get(search)) shouldBe Seq(caseWithStatusX1, caseWithStatusX2, caseWithStatusY1)
    }

    "get by filtering on statuses with some matches should return the expected documents" in {
      val search = Search(Filter(statuses = Some(Set(CaseStatus.NEW, CaseStatus.DRAFT))), None)
      store(caseWithStatusX1, caseWithStatusX2, caseWithStatusY1)
      await(repository.get(search)) shouldBe Seq(caseWithStatusX1, caseWithStatusX2)
    }

  }


  "get filtering by queueId, assigneeId and status" should {

    val assigneeX = Operator("assignee_x")
    val assigneeY = Operator("assignee_y")
    val queueIdX = Some("queue_x")
    val queueIdY = Some("queue_y")
    val statusX = CaseStatus.NEW
    val statusY = CaseStatus.OPEN

    val caseWithNoQueueAndNoAssignee = createCase()
    val caseWithQxAndAxAndSx = createCase().copy(queueId = queueIdX, assignee = Some(assigneeX), status = statusX)
    val caseWithQxAndAxAndSy = createCase().copy(queueId = queueIdX, assignee = Some(assigneeX), status = statusY)
    val caseWithQxAndAyAndSx = createCase().copy(queueId = queueIdX, assignee = Some(assigneeY), status = statusX)
    val caseWithQxAndAyAndSy = createCase().copy(queueId = queueIdX, assignee = Some(assigneeY), status = statusY)
    val caseWithQyAndAxAndSx = createCase().copy(queueId = queueIdY, assignee = Some(assigneeX), status = statusX)
    val caseWithQyAndAxAndSy = createCase().copy(queueId = queueIdY, assignee = Some(assigneeX), status = statusY)

    "get by filtering on assignee queue and status with one match should return the expected case" in {
      val search = Search(Filter(queueId = queueIdX, assigneeId = Some(assigneeX.id), statuses = Some(Set(CaseStatus.NEW))), None)

      store(
        caseWithNoQueueAndNoAssignee,
        caseWithQxAndAxAndSx,
        caseWithQxAndAxAndSy,
        caseWithQxAndAyAndSx,
        caseWithQxAndAyAndSy,
        caseWithQyAndAxAndSx,
        caseWithQyAndAxAndSy
      )
      await(repository.get(search)) shouldBe Seq(caseWithQxAndAxAndSx)
    }

  }

  private def store(cases: Case*): Unit = {
    cases.foreach { c: Case => await(repository.insert(c)) }
  }

  "getByReference" should {

    "retrieve the correct record" in {
      await(repository.insert(case1))
      collectionSize shouldBe 1

      await(repository.getByReference(case1.reference)) shouldBe Some(case1)
    }

    "return 'None' when the 'reference' doesn't match any record in the collection" in {
      for (_ <- 1 to 3) {
        await(repository.insert(createCase()))
      }
      collectionSize shouldBe 3

      await(repository.getByReference("WRONG_REFERENCE")) shouldBe None
    }
  }

  "incrementDaysElapsed" should {

    "increment NEW cases" in {
      val newCase1 = case1.copy(status = CaseStatus.NEW, daysElapsed = 0)
      val newCase2 = case2.copy(status = CaseStatus.NEW, daysElapsed = 1)

      await(repository.insert(newCase1))
      await(repository.insert(newCase2))
      collectionSize shouldBe 2

      await(repository.incrementDaysElapsed()) shouldBe 2

      await(repository.collection.find(selectorByReference(newCase1)).one[Case]).map(_.daysElapsed) shouldBe Some(1)
      await(repository.collection.find(selectorByReference(newCase2)).one[Case]).map(_.daysElapsed) shouldBe Some(2)
    }

    "increment OPEN cases" in {
      val openCase1 = case1.copy(status = CaseStatus.OPEN, daysElapsed = 0)
      val openCase2 = case2.copy(status = CaseStatus.OPEN, daysElapsed = 1)

      await(repository.insert(openCase1))
      await(repository.insert(openCase2))
      collectionSize shouldBe 2

      await(repository.incrementDaysElapsed()) shouldBe 2

      await(repository.collection.find(selectorByReference(openCase1)).one[Case]).map(_.daysElapsed) shouldBe Some(1)
      await(repository.collection.find(selectorByReference(openCase2)).one[Case]).map(_.daysElapsed) shouldBe Some(2)
    }

    "not increment other cases" in {
      val otherCase = case1.copy(status = CaseStatus.SUPPRESSED, daysElapsed = 0)

      await(repository.insert(otherCase))
      collectionSize shouldBe 1

      await(repository.incrementDaysElapsed()) shouldBe 0
    }
  }

  "The 'cases' collection" should {

    "have a unique index based on the field 'reference' " in {
      await(repository.insert(case1))
      val size = collectionSize

      val caught = intercept[DatabaseException] {

        await(repository.insert(case1.copy(status = CaseStatus.REFERRED)))
      }
      caught.code shouldBe Some(11000)

      collectionSize shouldBe size
    }

    "store dates as Mongo Dates" in {
      val date = Instant.now()
      val oldCase = case1.copy(createdDate = date)
      val newCase = case2.copy(createdDate = date.plusSeconds(1))
      await(repository.insert(oldCase))
      await(repository.insert(newCase))


      def selectAllWithSort(dir: Int): Future[Seq[Case]] = getMany(Json.obj(), Json.obj("createdDate" -> dir))

      await(selectAllWithSort(1)) shouldBe Seq(oldCase, newCase)
      await(selectAllWithSort(-1)) shouldBe Seq(newCase, oldCase)
    }

    "have all expected indexes" in {

      import scala.concurrent.duration._

      val expectedIndexes = List(
        Index(key = Seq("_id" -> Ascending), name = Some("_id_")),
        Index(key = Seq("reference" -> Ascending), name = Some("reference_Index"), unique = true),
        Index(key = Seq("queueId" -> Ascending), name = Some("queueId_Index"), unique = false),
        Index(key = Seq("daysElapsed" -> Ascending), name = Some("daysElapsed_Index"), unique = false),
        Index(key = Seq("application.holder.businessName" -> Ascending), name = Some("application.holder.businessName_Index"), unique = false),
        Index(key = Seq("assignee.id" -> Ascending), name = Some("assignee.id_Index"), unique = false),
        Index(key = Seq("decision.effectiveEndDate" -> Ascending), name = Some("decision.effectiveEndDate_Index"), unique = false),
        Index(key = Seq("status" -> Ascending), name = Some("status_Index"), unique = false)
      )

      val repo = createMongoRepo
      await(repo.ensureIndexes)

      eventually(timeout(5.seconds), interval(100.milliseconds)) {
        assertIndexes(expectedIndexes.sorted, getIndexes(repo.collection).sorted)
      }

      await(repo.drop)
    }
  }

  protected def getMany(filterBy: JsObject, sortBy: JsObject): Future[Seq[Case]] = {
    repository.collection
      .find[JsObject, Case](filterBy)
      .sort(sortBy)
      .cursor[Case]()
      .collect[Seq](Int.MaxValue, Cursor.FailOnError[Seq[Case]]())
  }

  private def selectorByReference(c: Case) = {
    BSONDocument("reference" -> c.reference)
  }

}
