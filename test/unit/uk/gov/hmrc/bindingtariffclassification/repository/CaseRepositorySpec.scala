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
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus._
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatCase
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.sort.{SortDirection, SortField}
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

  private val conflict = 11000

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

  "deleteAll" should {

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
      caught.code shouldBe Some(conflict)

      collectionSize shouldBe size
    }

  }

  "update" should {

    "modify an existing document in the collection" in {
      await(repository.insert(case1)) shouldBe case1
      val size = collectionSize

      val updated: Case = case1.copy(application = createBasicBTIApplication, status = CANCELLED)
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

  "get without search parameters" should {

    "retrieve all cases from the collection, sorted by insertion order" in {

      val search = Search()

      await(repository.insert(case1))
      await(repository.insert(case2))
      collectionSize shouldBe 2

      await(repository.get(search, Pagination())).results shouldBe Seq(case1, case2)
    }

    "return all cases from the collection sorted in ascending order" in {
      val search = Search(sort = Some(Sort(SortField.DAYS_ELAPSED, SortDirection.ASCENDING)))

      val oldCase = case1.copy(daysElapsed = 1)
      val newCase = case2.copy(daysElapsed = 0)
      await(repository.insert(oldCase))
      await(repository.insert(newCase))

      collectionSize shouldBe 2

      await(repository.get(search, Pagination())).results shouldBe Seq(newCase, oldCase)
    }

    "return all cases from the collection sorted in descending order" in {

      val search = Search(sort = Some(Sort(SortField.DAYS_ELAPSED, SortDirection.DESCENDING)))

      val oldCase = case1.copy(daysElapsed = 1)
      val newCase = case2.copy(daysElapsed = 0)
      await(repository.insert(oldCase))
      await(repository.insert(newCase))

      collectionSize shouldBe 2

      await(repository.get(search, Pagination())).results shouldBe Seq(oldCase, newCase)
    }

    "return an empty sequence when there are no cases in the collection" in {
      val search = Search()
      await(repository.get(search, Pagination())).results shouldBe Seq.empty[Case]
    }
  }

  "get by queueId" should {

    val queueIdX = Some("queue_x")
    val queueIdY = Some("queue_y")
    val unknownQueueId = Some("unknown_queue_id")

    val caseWithEmptyQueue = createCase()
    val caseWithQueueX1 = createCase().copy(queueId = queueIdX)
    val caseWithQueueX2 = createCase().copy(queueId = queueIdX)
    val caseWithQueueY = createCase().copy(queueId = queueIdY)

    "return an empty sequence when there are no matches" in {
      val search = Search(Filter(queueId = unknownQueueId))

      store(caseWithEmptyQueue, caseWithQueueX1)
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      val search = Search(Filter(queueId = queueIdX))
      store(caseWithEmptyQueue, caseWithQueueX1, caseWithQueueY)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithQueueX1)
    }

    "return the expected documents when there are multiple matches" in {
      val search = Search(Filter(queueId = queueIdX))

      store(caseWithEmptyQueue, caseWithQueueX1, caseWithQueueX2, caseWithQueueY)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithQueueX1, caseWithQueueX2)
    }

  }

  "get by minDecisionDate" should {

    val futureDate = LocalDate.of(3000,1,1).atStartOfDay().toInstant(ZoneOffset.UTC)
    val pastDate = LocalDate.of(1970,1,1).atStartOfDay().toInstant(ZoneOffset.UTC)

    val decisionExpired = createDecision(effectiveEndDate = Some(pastDate))
    val decisionFuture = createDecision(effectiveEndDate = Some(futureDate))
    val caseWithExpiredDecision = createCase(decision = Some(decisionExpired))
    val caseWithFutureDecision = createCase(decision = Some(decisionFuture))

    "return an empty sequence when there are no matches" in {
      val search = Search(Filter(minDecisionEnd = Some(Instant.now())))
      store(caseWithExpiredDecision)
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is a match" in {
      val search = Search(Filter(minDecisionEnd = Some(Instant.now())))
      store(caseWithExpiredDecision, caseWithFutureDecision)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithFutureDecision)
    }

  }

  "get by assigneeId" should {

    val assigneeX = Operator("assignee_x")
    val assigneeY = Operator("assignee_y")
    val unknownAssignee = Operator("unknown_assignee_id")

    val caseWithEmptyAssignee = createCase()
    val caseWithAssigneeX1 = createCase().copy(assignee = Some(assigneeX))
    val caseWithAssigneeX2 = createCase().copy(assignee = Some(assigneeX))
    val caseWithAssigneeY1 = createCase().copy(assignee = Some(assigneeY))

    "return an empty sequence when there are no matches" in {
      val search = Search(Filter(assigneeId = Some(unknownAssignee.id)))
      store(caseWithEmptyAssignee, caseWithAssigneeX1)
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      val search = Search(Filter(assigneeId = Some(assigneeX.id)))
      store(caseWithEmptyAssignee, caseWithAssigneeX1, caseWithAssigneeY1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithAssigneeX1)
    }

    "return the expected documents - with 'none'" in {
      val search = Search(Filter(assigneeId = Some("none")))
      store(caseWithEmptyAssignee, caseWithAssigneeX1, caseWithAssigneeY1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithEmptyAssignee)
    }

    "return the expected documents - with 'some'" in {
      val search = Search(Filter(assigneeId = Some("some")))
      store(caseWithEmptyAssignee, caseWithAssigneeX1, caseWithAssigneeY1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithAssigneeX1, caseWithAssigneeY1)
    }

    "return the expected documents when there are multiple matches" in {
      val search = Search(Filter(assigneeId = Some(assigneeX.id)))
      store(caseWithEmptyAssignee, caseWithAssigneeX1, caseWithAssigneeX2, caseWithAssigneeY1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithAssigneeX1, caseWithAssigneeX2)
    }

  }

  "get by single status" should {

    val caseWithStatusX1 = createCase().copy(status = NEW)
    val caseWithStatusX2 = createCase().copy(status = NEW)
    val caseWithStatusY1 = createCase().copy(status = OPEN)

    "return an empty sequence when there are no matches" in {
      val search = Search(Filter(statuses = Some(Set(DRAFT))))
      store(caseWithStatusX1)
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      val search = Search(Filter(statuses = Some(Set(NEW))))
      store(caseWithStatusX1, caseWithStatusY1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithStatusX1)
    }

    "return the expected documents when there are multiple matches" in {
      val search = Search(Filter(statuses = Some(Set(NEW))))
      store(caseWithStatusX1, caseWithStatusX2, caseWithStatusY1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithStatusX1, caseWithStatusX2)
    }

  }

  "get by multiple statuses" should {

    val caseWithStatusX1 = createCase().copy(status = NEW)
    val caseWithStatusX2 = createCase().copy(status = NEW)
    val caseWithStatusY1 = createCase().copy(status = OPEN)
    val caseWithStatusZ1 = createCase().copy(status = CANCELLED)
    val caseWithStatusW1 = createCase().copy(status = SUPPRESSED)

    "return an empty sequence when there are no matches" in {
      val search = Search(Filter(statuses = Some(Set(DRAFT,REFERRED))))
      store(caseWithStatusX1, caseWithStatusX2, caseWithStatusY1, caseWithStatusZ1, caseWithStatusW1)
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      val search = Search(Filter(statuses = Some(Set(NEW,REFERRED,SUSPENDED))))
      store(caseWithStatusX1, caseWithStatusY1, caseWithStatusZ1, caseWithStatusW1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithStatusX1)
    }

    "return the expected documents when there are multiple matches" in {
      val search = Search(Filter(statuses = Some(Set(NEW,DRAFT,OPEN))))
      store(caseWithStatusX1, caseWithStatusX2, caseWithStatusY1, caseWithStatusZ1, caseWithStatusW1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithStatusX1, caseWithStatusX2, caseWithStatusY1)
    }

  }

  "get by single keyword" should {

    val c1 = createCase(keywords = Set("BIKE", "MTB"))
    val c2 = createCase(keywords = Set("KNIFE", "KITCHEN"))
    val c3 = createCase(keywords = Set("BIKE", "HARDTAIL"))

    "return an empty sequence when there are no matches" in {
      store(case1, c1)
      val search = Search(Filter(keywords = Some(Set("KNIFE"))))
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      store(case1, c1, c2)
      val search = Search(Filter(keywords = Some(Set("KNIFE"))))
      await(repository.get(search, Pagination())).results shouldBe Seq(c2)
    }

    "return the expected documents when there are multiple matches" in {
      store(case1, c1, c2, c3)
      val search = Search(Filter(keywords = Some(Set("BIKE"))))
      await(repository.get(search, Pagination())).results shouldBe Seq(c1, c3)
    }

  }

  "get by multiple keywords" should {

    val c1 = createCase(keywords = Set("BIKE", "MTB"))
    val c2 = createCase(keywords = Set("BIKE", "MTB", "29ER"))
    val c3 = createCase(keywords = Set("BIKE", "HARDTAIL"))

    "return an empty sequence when there are no matches" in {
      store(case1, c1, c2, c3)
      val search = Search(Filter(keywords = Some(Set("BIKE", "MTB", "HARDTAIL"))))
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      store(case1, c1, c2, c3)
      val search = Search(Filter(keywords = Some(Set("BIKE", "MTB", "29ER"))))
      await(repository.get(search, Pagination())).results shouldBe Seq(c2)
    }

    "return the expected documents when there are multiple matches" in {
      store(case1, c1, c2, c3)
      val search = Search(Filter(keywords = Some(Set("BIKE", "MTB"))))
      await(repository.get(search, Pagination())).results shouldBe Seq(c1, c2)
    }

  }

  "get by trader name" should {

    val novakApp = createBasicBTIApplication.copy(holder = createEORIDetails.copy(businessName = "Novak Djokovic"))
    val caseX = createCase(app = novakApp)

    "return an empty sequence when there are no matches" in {
      store(case1, caseX)

      await(repository.get(Search(Filter(traderName = Some("Alfred"))), Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      store(case1, caseX)

      // full name search
      await(repository.get(Search(Filter(traderName = Some("Novak Djokovic"))), Pagination())).results shouldBe Seq(caseX)

      // substring search
      await(repository.get(Search(Filter(traderName = Some("Novak"))), Pagination())).results shouldBe Seq(caseX)
      await(repository.get(Search(Filter(traderName = Some("Djokovic"))), Pagination())).results shouldBe Seq(caseX)

      // case-insensitive
      await(repository.get(Search(Filter(traderName = Some("novak djokovic"))), Pagination())).results shouldBe Seq(caseX)
    }

    "return the expected documents when there are multiple matches" in {
      val novakApp2 = createBasicBTIApplication.copy(holder = createEORIDetails.copy(businessName = "Novak Djokovic 2"))
      val caseX2 = createCase(app = novakApp2)
      store(caseX, caseX2)

      val search = Search(Filter(traderName = Some("Novak Djokovic")))
      await(repository.get(search, Pagination())).results shouldBe Seq(caseX, caseX2)
    }
  }

  "get by eori" should {

    val holderEori = "01234"
    val agentEori = "98765"

    val agentDetails = createAgentDetails.copy(eoriDetails = createEORIDetails.copy(eori = agentEori))

    val holderApp = createBasicBTIApplication.copy(holder = createEORIDetails.copy(eori = holderEori), agent = None)
    val agentApp = createBTIApplicationWithAllFields.copy(holder = createEORIDetails.copy(eori = holderEori), agent = Some(agentDetails))

    val agentCase = createCase(app = agentApp)
    val holderCase = createCase(app = holderApp)

    "return an empty sequence when there are no matches" in {
      store(agentCase, holderCase)

      await(repository.get(Search(Filter(eori = Some("333"))), Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      store(agentCase, holderCase)

      await(repository.get(Search(Filter(eori = Some("98765"))), Pagination())).results shouldBe Seq(agentCase)
    }

    "return the expected documents when there are multiple matches" in {
      store(agentCase, holderCase)

      await(repository.get(Search(Filter(eori = Some("01234"))), Pagination())).results shouldBe Seq(agentCase, holderCase)
    }

  }

  "get by decision details" should {

    val c1 = createCase(decision = Some(createDecision(goodsDescription = "Amazing HTC smartphone")))
    val c2 = createCase(decision = Some(createDecision(methodCommercialDenomination = Some("amazing football shoes"))))
    val c3 = createCase(decision = Some(createDecision(justification = "this is absolutely AAAAMAZINGGGG")))

    "return an empty sequence when there are no matches" in {
      store(case1, c1, c2, c3)
      await(repository.get(Search(Filter(decisionDetails = Some("table"))), Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      store(case1, c1, c2, c3)
      await(repository.get(Search(Filter(decisionDetails = Some("Football"))), Pagination())).results shouldBe Seq(c2)
    }

    "return the expected documents when there are multiple matches" in {
      store(case1, c1, c2, c3)
      await(repository.get(Search(Filter(decisionDetails = Some("amazing"))), Pagination())).results shouldBe Seq(c1, c2, c3)
    }
  }

  "get by commodity code name" should {

    val caseX = createNewCaseWithExtraFields()
    val caseY = createNewCaseWithExtraFields().copy(reference = "88888888")

    "return an empty sequence when there are no matches" in {
      store(case1)
      val search = Search(Filter(commodityCode = Some("234")))
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      store(caseX, case1)
      val search = Search(Filter(commodityCode = Some("12345")))
      await(repository.get(search, Pagination())).results shouldBe Seq(caseX)
    }

    "return the expected documents when there are multiple matches" in {
      store(case1, caseX, caseY)
      val search = Search(Filter(commodityCode = Some("12345")))
      await(repository.get(search, Pagination())).results shouldBe Seq(caseX, caseY)
    }
  }

  "pagination" should {

    "return some cases with default Pagination" in {
      store(case1)
      store(case2)
      await(repository.get(Search(), Pagination())).size shouldBe 2
    }

    "return upto 'pageSize' cases" in {
      store(case1)
      store(case2)
      await(repository.get(Search(), Pagination(pageSize = 1))).size shouldBe 1
    }

    "return pages of cases" in {
      store(case1)
      store(case2)
      await(repository.get(Search(), Pagination(pageSize = 1))).size shouldBe 1
      await(repository.get(Search(), Pagination(page = 2, pageSize = 1))).size shouldBe 1
      await(repository.get(Search(), Pagination(page = 3, pageSize = 1))).size shouldBe 0
    }
  }

  "get by queueId, assigneeId and status" should {

    val assigneeX = Operator("assignee_x")
    val assigneeY = Operator("assignee_y")
    val queueIdX = Some("queue_x")
    val queueIdY = Some("queue_y")
    val statusX = NEW
    val statusY = OPEN

    val caseWithNoQueueAndNoAssignee = createCase()
    val caseWithQxAndAxAndSx = createCase().copy(queueId = queueIdX, assignee = Some(assigneeX), status = statusX)
    val caseWithQxAndAxAndSy = createCase().copy(queueId = queueIdX, assignee = Some(assigneeX), status = statusY)
    val caseWithQxAndAyAndSx = createCase().copy(queueId = queueIdX, assignee = Some(assigneeY), status = statusX)
    val caseWithQxAndAyAndSy = createCase().copy(queueId = queueIdX, assignee = Some(assigneeY), status = statusY)
    val caseWithQyAndAxAndSx = createCase().copy(queueId = queueIdY, assignee = Some(assigneeX), status = statusX)
    val caseWithQyAndAxAndSy = createCase().copy(queueId = queueIdY, assignee = Some(assigneeX), status = statusY)

    "filter as expected" in {
      val search = Search(Filter(queueId = queueIdX, assigneeId = Some(assigneeX.id), statuses = Some(Set(NEW))))

      store(
        caseWithNoQueueAndNoAssignee,
        caseWithQxAndAxAndSx,
        caseWithQxAndAxAndSy,
        caseWithQxAndAyAndSx,
        caseWithQxAndAyAndSy,
        caseWithQyAndAxAndSx,
        caseWithQyAndAxAndSy
      )
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithQxAndAxAndSx)
    }

  }

  "get by reference" should {

    "retrieve the correct document" in {
      await(repository.insert(case1))
      collectionSize shouldBe 1

      await(repository.getByReference(case1.reference)) shouldBe Some(case1)
    }

    "return 'None' when the 'reference' doesn't match any document in the collection" in {
      for (_ <- 1 to 3) {
        await(repository.insert(createCase()))
      }
      collectionSize shouldBe 3

      await(repository.getByReference("WRONG_REFERENCE")) shouldBe None
    }
  }

  "increment days elapsed" should {

    "increment NEW cases" in {
      val newCase1 = case1.copy(status = NEW, daysElapsed = 0)
      val newCase2 = case2.copy(status = NEW, daysElapsed = 1)

      await(repository.insert(newCase1))
      await(repository.insert(newCase2))
      collectionSize shouldBe 2

      await(repository.incrementDaysElapsed()) shouldBe 2

      await(repository.collection.find(selectorByReference(newCase1)).one[Case]).map(_.daysElapsed) shouldBe Some(1)
      await(repository.collection.find(selectorByReference(newCase2)).one[Case]).map(_.daysElapsed) shouldBe Some(2)
    }

    "increment OPEN cases" in {
      val openCase1 = case1.copy(status = OPEN, daysElapsed = 0)
      val openCase2 = case2.copy(status = OPEN, daysElapsed = 1)

      await(repository.insert(openCase1))
      await(repository.insert(openCase2))
      collectionSize shouldBe 2

      await(repository.incrementDaysElapsed()) shouldBe 2

      await(repository.collection.find(selectorByReference(openCase1)).one[Case]).map(_.daysElapsed) shouldBe Some(1)
      await(repository.collection.find(selectorByReference(openCase2)).one[Case]).map(_.daysElapsed) shouldBe Some(2)
    }

    "not increment other cases" in {
      val otherCase = case1.copy(status = SUPPRESSED, daysElapsed = 0)

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

        await(repository.insert(case1.copy(status = REFERRED)))
      }
      caught.code shouldBe Some(conflict)

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
        Index(key = Seq("application.holder.eori" -> Ascending), name = Some("application.holder.eori_Index"), unique = false),
        Index(key = Seq("application.agent.eoriDetails.eori" -> Ascending), name = Some("application.agent.eoriDetails.eori_Index"), unique = false),
        Index(key = Seq("daysElapsed" -> Ascending), name = Some("daysElapsed_Index"), unique = false),
        Index(key = Seq("assignee.id" -> Ascending), name = Some("assignee.id_Index"), unique = false),
        Index(key = Seq("decision.effectiveEndDate" -> Ascending), name = Some("decision.effectiveEndDate_Index"), unique = false),
        Index(key = Seq("decision.bindingCommodityCode" -> Ascending), name = Some("decision.bindingCommodityCode_Index"), unique = false),
        Index(key = Seq("status" -> Ascending), name = Some("status_Index"), unique = false),
        Index(key = Seq("keywords" -> Ascending), name = Some("keywords_Index"), unique = false)
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

  private def store(cases: Case*): Unit = {
    cases.foreach { c: Case => await(repository.insert(c)) }
  }

}
