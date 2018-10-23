/*
 * Copyright 2018 HM Revenue & Customs
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

package unit.uk.gov.hmrc.bindingtariffclassification.repository

import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import reactivemongo.api.DB
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson._
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.JsonFormatters.formatCase
import uk.gov.hmrc.bindingtariffclassification.model.search.SearchCase
import uk.gov.hmrc.bindingtariffclassification.repository.{BaseMongoIndexSpec, CaseMongoRepository, MongoDbProvider}
import uk.gov.hmrc.bindingtariffclassification.todelete.CaseData._
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global

class CaseRepositorySpec extends BaseMongoIndexSpec
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with MongoSpecSupport
  with Eventually {
  self =>

  private val mongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private def getIndexes(repo: CaseMongoRepository): List[Index] = {
    val indexesFuture = repo.collection.indexesManager.list()
    await(indexesFuture)
  }

  private val repository = new CaseMongoRepository(mongoDbProvider)

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

  "insert" should {

    "insert a new document in the collection" in {
      collectionSize shouldBe 0

      await(repository.insert(case1)) shouldBe case1
      collectionSize shouldBe 1
      await(repository.collection.find(selectorByReference(case1)).one[Case]) shouldBe Some(case1)
    }

    "fail to insert an existing document in the collection" in {
      collectionSize shouldBe 0

      await(repository.insert(case1)) shouldBe case1
      collectionSize shouldBe 1

      val caught = intercept[DatabaseException] {
        await(repository.insert(case1))
      }
      caught.code shouldBe Some(11000)

      collectionSize shouldBe 1
    }

  }

  "update" should {

    "modify an existing document in the collection" in {
      collectionSize shouldBe 0

      await(repository.insert(case1)) shouldBe case1
      collectionSize shouldBe 1

      val updated: Case = case1.copy(application = createBTIApplication, status = CaseStatus.CANCELLED)
      await(repository.update(updated)) shouldBe Some(updated)
      collectionSize shouldBe 1

      await(repository.collection.find(selectorByReference(updated)).one[Case]) shouldBe Some(updated)
    }

    "do nothing when try to update a non existing document in the collection" in {
      collectionSize shouldBe 0

      await(repository.update(case1)) shouldBe None
      collectionSize shouldBe 0
    }
  }

  // TODO: test all possible combinations
  // TODO: the test scenarios titles need to be written and grouped properly

  "get without params should return all cases" should {

    "retrieve all cases from the collection" in {
      await(repository.insert(case1))
      await(repository.insert(case2))
      collectionSize shouldBe 2

      await(repository.get()) shouldBe Seq(case1, case2)
    }

    "return an empty sequence when there are no cases in the collection" in {
      await(repository.get()) shouldBe Seq.empty
    }
  }

  "get filtering by Queue" should {

    val queueIdX = Some("queue_x")
    val queueIdY = Some("queue_y")
    val unknownQueueId = Some("unknown_queue_id")

    val caseWithEmptyQueue = createCase()
    val caseWithQueueX1 = createCase().copy(queueId = queueIdX)
    val caseWithQueueX2 = createCase().copy(queueId = queueIdX)
    val caseWithQueueY = createCase().copy(queueId = queueIdY)

    "get by filtering on queueId with no matches should return empty sequence" in {

      store(caseWithEmptyQueue, caseWithQueueX1)
      await(repository.get(Some(SearchCase(queueId = unknownQueueId)))) shouldBe Seq.empty
    }

    "get by filtering on queueId with one match should return just this queue" in {

      store(caseWithEmptyQueue, caseWithQueueX1, caseWithQueueY)
      await(repository.get(Some(SearchCase(queueId = queueIdX)))) shouldBe Seq(caseWithQueueX1)
    }

    "get by filtering on queueId with two matches should return just those queues" in {
      store(caseWithEmptyQueue, caseWithQueueX1, caseWithQueueX2, caseWithQueueY)
      await(repository.get(Some(SearchCase(queueId = queueIdX)))) shouldBe Seq(caseWithQueueX1, caseWithQueueX2)
    }

  }

  "get filtering by AssigneeId" should {

    val assigneeX = Some("assignee_x")
    val assigneeY = Some("assignee_y")
    val unknownAssignee = Some("unknown_assignee_id")

    val caseWithEmptyAssignee = createCase()
    val caseWithAssigneeX1 = createCase().copy(assigneeId = assigneeX)
    val caseWithAssigneeX2 = createCase().copy(assigneeId = assigneeX)
    val caseWithAssigneeY1 = createCase().copy(assigneeId = assigneeY)

    "get by filtering on assignee with no matches should return empty sequence" in {

      store(caseWithEmptyAssignee, caseWithAssigneeX1)
      await(repository.get(Some(SearchCase(assigneeId = unknownAssignee)))) shouldBe Seq.empty
    }

    "get by filtering on assignee with one match should return just this one" in {

      store(caseWithEmptyAssignee, caseWithAssigneeX1, caseWithAssigneeY1)
      await(repository.get(Some(SearchCase(assigneeId = assigneeX)))) shouldBe Seq(caseWithAssigneeX1)
    }

    "get by filtering on assignee with two matches should return just those two" in {
      store(caseWithEmptyAssignee, caseWithAssigneeX1, caseWithAssigneeX2, caseWithAssigneeY1)
      await(repository.get(Some(SearchCase(assigneeId = assigneeX)))) shouldBe Seq(caseWithAssigneeX1, caseWithAssigneeX2)
    }

  }


  "get filtering by QueueId and AssigneeId" should {

    val assigneeX = Some("assignee_x")
    val assigneeY = Some("assignee_y")
    val queueIdX = Some("queue_x")
    val queueIdY = Some("queue_y")

    val caseWithNoQueueAndNoAssignee = createCase()
    val caseWithQxAndAx = createCase().copy(queueId = queueIdX, assigneeId = assigneeX)
    val caseWithQxAndAy = createCase().copy(queueId = queueIdX, assigneeId = assigneeY)
    val caseWithQyAndAx = createCase().copy(queueId = queueIdY, assigneeId = assigneeX)


    "get by filtering on assignee and queue with one matches should return just this one" in {
      store(caseWithNoQueueAndNoAssignee, caseWithQxAndAx, caseWithQxAndAy, caseWithQyAndAx)
      await(repository.get(Some(SearchCase(queueId = queueIdX, assigneeId = assigneeX)))) shouldBe Seq(caseWithQxAndAx)
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

  "The 'cases' collection" should {

    "have a unique index based on the field 'reference' " in {
      await(repository.insert(case1))
      collectionSize shouldBe 1

      val caught = intercept[DatabaseException] {

        await(repository.insert(case1.copy(status = CaseStatus.REFERRED)))
      }
      caught.code shouldBe Some(11000)

      collectionSize shouldBe 1
    }

    "have all expected indexes" in {

      import scala.concurrent.duration._

      val expectedIndexes = List(
        Index(key = Seq("reference" -> Ascending), name = Some("reference_Index"), unique = true, background = true),
        Index(key = Seq("queueId" -> Ascending), name = Some("queueId_Index"), unique = false, background = true),
        Index(key = Seq("assigneeId" -> Ascending), name = Some("assigneeId_Index"), unique = false, background = true),
        Index(key = Seq("_id" -> Ascending), name = Some("_id_"))
      )

      val repo = new CaseMongoRepository(mongoDbProvider)

      eventually(timeout(5.seconds), interval(100.milliseconds)) {
        assertIndexes(expectedIndexes.sorted, getIndexes(repo).sorted)
      }
    }
  }

  private def selectorByReference(c: Case) = {
    BSONDocument("reference" -> c.reference)
  }

}
