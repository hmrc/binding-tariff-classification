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

import java.time._

import org.mockito.BDDMockito.given
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.libs.json.{JsObject, Json}
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.{Cursor, DB, ReadConcern}
import reactivemongo.bson._
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatCase
import uk.gov.hmrc.bindingtariffclassification.model.reporting._
import uk.gov.hmrc.bindingtariffclassification.model.{CaseStatus, PseudoCaseStatus, _}
import uk.gov.hmrc.bindingtariffclassification.sort.{CaseSortField, SortDirection}
import uk.gov.hmrc.mongo.MongoSpecSupport
import util.CaseData._
import util.Cases._

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

  private val mongoDbProvider: MongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private val config = mock[AppConfig]
  private val repository = newMongoRepository

  private def newMongoRepository: CaseMongoRepository = new CaseMongoRepository(mongoDbProvider, new SearchMapper(config))

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
    await(repository.collection.count(selector = None, limit = Some(0), skip = 0, hint = None, readConcern = ReadConcern.Local)).toInt
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

      val updated: Case = case1.copy(application = createBasicBTIApplication, status = CaseStatus.CANCELLED)
      await(repository.update(updated, upsert = false)) shouldBe Some(updated)
      collectionSize shouldBe size

      await(repository.collection.find(selectorByReference(updated)).one[Case]) shouldBe Some(updated)
    }

    "do nothing when trying to update an unknown document" in {
      val size = collectionSize

      await(repository.update(case1, upsert = false)) shouldBe None
      collectionSize shouldBe size
    }

    "upsert a new existing document in the collection" in {
      val size = collectionSize

      await(repository.update(case1, upsert = true)) shouldBe Some(case1)
      collectionSize shouldBe size + 1
    }
  }

  "get without search parameters" should {

    "retrieve all cases from the collection, sorted by insertion order" in {
      val search = CaseSearch()

      await(repository.insert(case1))
      await(repository.insert(case2))
      collectionSize shouldBe 2

      await(repository.get(search, Pagination())).results shouldBe Seq(case1, case2)
    }

    "return all cases from the collection sorted in ascending order" in {
      val search = CaseSearch(sort = Some(CaseSort(CaseSortField.DAYS_ELAPSED, SortDirection.ASCENDING)))

      val oldCase = case1.copy(daysElapsed = 1)
      val newCase = case2.copy(daysElapsed = 0)
      await(repository.insert(oldCase))
      await(repository.insert(newCase))

      collectionSize shouldBe 2

      await(repository.get(search, Pagination())).results shouldBe Seq(newCase, oldCase)
    }

    "return all cases from the collection sorted in descending order" in {

      val search = CaseSearch(sort = Some(CaseSort(CaseSortField.DAYS_ELAPSED, SortDirection.DESCENDING)))

      val oldCase = case1.copy(daysElapsed = 1)
      val newCase = case2.copy(daysElapsed = 0)
      await(repository.insert(oldCase))
      await(repository.insert(newCase))

      collectionSize shouldBe 2

      await(repository.get(search, Pagination())).results shouldBe Seq(oldCase, newCase)
    }

    "return an empty sequence when there are no cases in the collection" in {
      val search = CaseSearch()
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
      val search = CaseSearch(CaseFilter(queueId = unknownQueueId))

      store(caseWithEmptyQueue, caseWithQueueX1)
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      val search = CaseSearch(CaseFilter(queueId = queueIdX))
      store(caseWithEmptyQueue, caseWithQueueX1, caseWithQueueY)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithQueueX1)
    }

    "return the expected documents when there are multiple matches" in {
      val search = CaseSearch(CaseFilter(queueId = queueIdX))

      store(caseWithEmptyQueue, caseWithQueueX1, caseWithQueueX2, caseWithQueueY)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithQueueX1, caseWithQueueX2)
    }

  }

  "get by minDecisionDate" should {

    val futureDate = LocalDate.of(3000, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
    val pastDate = LocalDate.of(1970, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)

    val decisionExpired = createDecision(effectiveEndDate = Some(pastDate))
    val decisionFuture = createDecision(effectiveEndDate = Some(futureDate))
    val caseWithExpiredDecision = createCase(decision = Some(decisionExpired))
    val caseWithFutureDecision = createCase(decision = Some(decisionFuture))

    "return an empty sequence when there are no matches" in {
      val search = CaseSearch(CaseFilter(minDecisionEnd = Some(Instant.now())))
      store(caseWithExpiredDecision)
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is a match" in {
      val search = CaseSearch(CaseFilter(minDecisionEnd = Some(Instant.now())))
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
      val search = CaseSearch(CaseFilter(assigneeId = Some(unknownAssignee.id)))
      store(caseWithEmptyAssignee, caseWithAssigneeX1)
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      val search = CaseSearch(CaseFilter(assigneeId = Some(assigneeX.id)))
      store(caseWithEmptyAssignee, caseWithAssigneeX1, caseWithAssigneeY1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithAssigneeX1)
    }

    "return the expected documents - with 'none'" in {
      val search = CaseSearch(CaseFilter(assigneeId = Some("none")))
      store(caseWithEmptyAssignee, caseWithAssigneeX1, caseWithAssigneeY1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithEmptyAssignee)
    }

    "return the expected documents - with 'some'" in {
      val search = CaseSearch(CaseFilter(assigneeId = Some("some")))
      store(caseWithEmptyAssignee, caseWithAssigneeX1, caseWithAssigneeY1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithAssigneeX1, caseWithAssigneeY1)
    }

    "return the expected documents when there are multiple matches" in {
      val search = CaseSearch(CaseFilter(assigneeId = Some(assigneeX.id)))
      store(caseWithEmptyAssignee, caseWithAssigneeX1, caseWithAssigneeX2, caseWithAssigneeY1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithAssigneeX1, caseWithAssigneeX2)
    }

  }

  "get by concrete status" should {

    val caseWithStatusX1 = createCase().copy(status = CaseStatus.NEW)
    val caseWithStatusX2 = createCase().copy(status = CaseStatus.NEW)
    val caseWithStatusY1 = createCase().copy(status = CaseStatus.OPEN)

    "return an empty sequence when there are no matches" in {
      val search = CaseSearch(CaseFilter(statuses = Some(Set(PseudoCaseStatus.DRAFT))))
      store(caseWithStatusX1)
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      val search = CaseSearch(CaseFilter(statuses = Some(Set(PseudoCaseStatus.NEW))))
      store(caseWithStatusX1, caseWithStatusY1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithStatusX1)
    }

    "return the expected documents when there are multiple matches" in {
      val search = CaseSearch(CaseFilter(statuses = Some(Set(PseudoCaseStatus.NEW))))
      store(caseWithStatusX1, caseWithStatusX2, caseWithStatusY1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithStatusX1, caseWithStatusX2)
    }

  }

  "get by pseudo status" should {
    val now = LocalDateTime.of(2019, 1, 1, 0, 0).toInstant(ZoneOffset.UTC)

    val newCase = createCase(r = "new", status = CaseStatus.NEW)
    val liveCase = createCase(r = "live", status = CaseStatus.COMPLETED, decision = Some(createDecision(effectiveEndDate = Some(now.plusSeconds(1)))))
    val expiredCase = createCase(r = "expired", status = CaseStatus.COMPLETED, decision = Some(createDecision(effectiveEndDate = Some(now.minusSeconds(1)))))

    "return an empty sequence when there are no matches" in {
      given(config.clock) willReturn Clock.fixed(now, ZoneOffset.UTC)

      store(newCase)
      val search = CaseSearch(CaseFilter(statuses = Some(Set(PseudoCaseStatus.LIVE))))
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      given(config.clock) willReturn Clock.fixed(now, ZoneOffset.UTC)

      store(newCase, liveCase, expiredCase)
      val search = CaseSearch(CaseFilter(statuses = Some(Set(PseudoCaseStatus.LIVE))))
      await(repository.get(search, Pagination())).results shouldBe Seq(liveCase)
    }

    "return the expected documents when there are multiple matches" in {
      given(config.clock) willReturn Clock.fixed(now, ZoneOffset.UTC)

      store(newCase, liveCase, expiredCase)
      val search = CaseSearch(CaseFilter(statuses = Some(Set(PseudoCaseStatus.LIVE, PseudoCaseStatus.EXPIRED))))
      await(repository.get(search, Pagination())).results shouldBe Seq(expiredCase, liveCase)
    }

  }

  "get by multiple statuses" should {

    val caseWithStatusX1 = createCase().copy(status = CaseStatus.NEW)
    val caseWithStatusX2 = createCase().copy(status = CaseStatus.NEW)
    val caseWithStatusY1 = createCase().copy(status = CaseStatus.OPEN)
    val caseWithStatusZ1 = createCase().copy(status = CaseStatus.CANCELLED)
    val caseWithStatusW1 = createCase().copy(status = CaseStatus.SUPPRESSED)

    "return an empty sequence when there are no matches" in {
      val search = CaseSearch(CaseFilter(statuses = Some(Set(PseudoCaseStatus.DRAFT, PseudoCaseStatus.REFERRED))))
      store(caseWithStatusX1, caseWithStatusX2, caseWithStatusY1, caseWithStatusZ1, caseWithStatusW1)
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      val search = CaseSearch(CaseFilter(statuses = Some(Set(PseudoCaseStatus.NEW, PseudoCaseStatus.REFERRED, PseudoCaseStatus.SUSPENDED))))
      store(caseWithStatusX1, caseWithStatusY1, caseWithStatusZ1, caseWithStatusW1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithStatusX1)
    }

    "return the expected documents when there are multiple matches" in {
      val search = CaseSearch(CaseFilter(statuses = Some(Set(PseudoCaseStatus.NEW, PseudoCaseStatus.DRAFT, PseudoCaseStatus.OPEN))))
      store(caseWithStatusX1, caseWithStatusX2, caseWithStatusY1, caseWithStatusZ1, caseWithStatusW1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithStatusX1, caseWithStatusX2, caseWithStatusY1)
    }

  }

  "get by multiple references" should {
    val caseWithReferenceX1 = createCase().copy(reference = "x1")
    val caseWithReferenceX2 = createCase().copy(reference = "x2")
    val caseWithReferenceY1 = createCase().copy(reference = "y1")
    val caseWithReferenceZ1 = createCase().copy(reference = "z1")
    val caseWithReferenceW1 = createCase().copy(reference = "w1")

    "return an empty sequence when there are no matches" in {
      val search = CaseSearch(CaseFilter(reference = Some(Set("a", "b"))))
      store(caseWithReferenceX1, caseWithReferenceX2, caseWithReferenceY1, caseWithReferenceZ1, caseWithReferenceW1)
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      val search = CaseSearch(CaseFilter(reference = Some(Set("x1"))))
      store(caseWithReferenceX1, caseWithReferenceY1, caseWithReferenceZ1, caseWithReferenceW1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithReferenceX1)
    }

    "return the expected documents when there are multiple matches" in {
      val search = CaseSearch(CaseFilter(reference = Some(Set("x1", "x2", "y1"))))
      store(caseWithReferenceX1, caseWithReferenceX2, caseWithReferenceY1, caseWithReferenceZ1, caseWithReferenceW1)
      await(repository.get(search, Pagination())).results shouldBe Seq(caseWithReferenceX1, caseWithReferenceX2, caseWithReferenceY1)
    }

  }

  "get by single keyword" should {

    val c1 = createCase(keywords = Set("BIKE", "MTB"))
    val c2 = createCase(keywords = Set("KNIFE", "KITCHEN"))
    val c3 = createCase(keywords = Set("BIKE", "HARDTAIL"))

    "return an empty sequence when there are no matches" in {
      store(case1, c1)
      val search = CaseSearch(CaseFilter(keywords = Some(Set("KNIFE"))))
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      store(case1, c1, c2)
      val search = CaseSearch(CaseFilter(keywords = Some(Set("KNIFE"))))
      await(repository.get(search, Pagination())).results shouldBe Seq(c2)
    }

    "return the expected documents when there are multiple matches" in {
      store(case1, c1, c2, c3)
      val search = CaseSearch(CaseFilter(keywords = Some(Set("BIKE"))))
      await(repository.get(search, Pagination())).results shouldBe Seq(c1, c3)
    }

  }

  "get by multiple keywords" should {

    val c1 = createCase(keywords = Set("BIKE", "MTB"))
    val c2 = createCase(keywords = Set("BIKE", "MTB", "29ER"))
    val c3 = createCase(keywords = Set("BIKE", "HARDTAIL"))

    "return an empty sequence when there are no matches" in {
      store(case1, c1, c2, c3)
      val search = CaseSearch(CaseFilter(keywords = Some(Set("BIKE", "MTB", "HARDTAIL"))))
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      store(case1, c1, c2, c3)
      val search = CaseSearch(CaseFilter(keywords = Some(Set("BIKE", "MTB", "29ER"))))
      await(repository.get(search, Pagination())).results shouldBe Seq(c2)
    }

    "return the expected documents when there are multiple matches" in {
      store(case1, c1, c2, c3)
      val search = CaseSearch(CaseFilter(keywords = Some(Set("BIKE", "MTB"))))
      await(repository.get(search, Pagination())).results shouldBe Seq(c1, c2)
    }

  }

  "get by trader name" should {

    val novakApp = createBasicBTIApplication.copy(holder = createEORIDetails.copy(businessName = "Novak Djokovic"))
    val caseX = createCase(app = novakApp)

    "return an empty sequence when there are no matches" in {
      store(case1, caseX)

      await(repository.get(CaseSearch(CaseFilter(traderName = Some("Alfred"))), Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      store(case1, caseX)

      // full name search
      await(repository.get(CaseSearch(CaseFilter(traderName = Some("Novak Djokovic"))), Pagination())).results shouldBe Seq(caseX)

      // substring search
      await(repository.get(CaseSearch(CaseFilter(traderName = Some("Novak"))), Pagination())).results shouldBe Seq(caseX)
      await(repository.get(CaseSearch(CaseFilter(traderName = Some("Djokovic"))), Pagination())).results shouldBe Seq(caseX)

      // case-insensitive
      await(repository.get(CaseSearch(CaseFilter(traderName = Some("novak djokovic"))), Pagination())).results shouldBe Seq(caseX)
    }

    "return the expected documents when there are multiple matches" in {
      val novakApp2 = createBasicBTIApplication.copy(holder = createEORIDetails.copy(businessName = "Novak Djokovic 2"))
      val caseX2 = createCase(app = novakApp2)
      store(caseX, caseX2)

      val search = CaseSearch(CaseFilter(traderName = Some("Novak Djokovic")))
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

      await(repository.get(CaseSearch(CaseFilter(eori = Some("333"))), Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      store(agentCase, holderCase)

      await(repository.get(CaseSearch(CaseFilter(eori = Some("98765"))), Pagination())).results shouldBe Seq(agentCase)
    }

    "return the expected documents when there are multiple matches" in {
      store(agentCase, holderCase)

      await(repository.get(CaseSearch(CaseFilter(eori = Some("01234"))), Pagination())).results shouldBe Seq(agentCase, holderCase)
    }

  }

  "get by decision details" should {

    val c1 = createCase(decision = Some(createDecision(goodsDescription = "Amazing HTC smartphone")))
    val c2 = createCase(decision = Some(createDecision(methodCommercialDenomination = Some("amazing football shoes"))))
    val c3 = createCase(decision = Some(createDecision(justification = "this is absolutely AAAAMAZINGGGG")))

    "return an empty sequence when there are no matches" in {
      store(case1, c1, c2, c3)
      await(repository.get(CaseSearch(CaseFilter(decisionDetails = Some("table"))), Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      store(case1, c1, c2, c3)
      await(repository.get(CaseSearch(CaseFilter(decisionDetails = Some("Football"))), Pagination())).results shouldBe Seq(c2)
    }

    "return the expected documents when there are multiple matches" in {
      store(case1, c1, c2, c3)
      await(repository.get(CaseSearch(CaseFilter(decisionDetails = Some("amazing"))), Pagination())).results shouldBe Seq(c1, c2, c3)
    }
  }

  "get by commodity code name" should {

    val caseX = createNewCaseWithExtraFields()
    val caseY = createNewCaseWithExtraFields().copy(reference = "88888888")

    "return an empty sequence when there are no matches" in {
      store(case1)
      val search = CaseSearch(CaseFilter(commodityCode = Some("234")))
      await(repository.get(search, Pagination())).results shouldBe Seq.empty
    }

    "return the expected document when there is one match" in {
      store(caseX, case1)
      val search = CaseSearch(CaseFilter(commodityCode = Some("12345")))
      await(repository.get(search, Pagination())).results shouldBe Seq(caseX)
    }

    "return the expected documents when there are multiple matches" in {
      store(case1, caseX, caseY)
      val search = CaseSearch(CaseFilter(commodityCode = Some("12345")))
      await(repository.get(search, Pagination())).results shouldBe Seq(caseX, caseY)
    }
  }

  "pagination" should {

    "return some cases with default Pagination" in {
      store(case1)
      store(case2)
      await(repository.get(CaseSearch(), Pagination())).size shouldBe 2
    }

    "return upto 'pageSize' cases" in {
      store(case1)
      store(case2)
      await(repository.get(CaseSearch(), Pagination(pageSize = 1))).size shouldBe 1
    }

    "return pages of cases" in {
      store(case1)
      store(case2)
      await(repository.get(CaseSearch(), Pagination(pageSize = 1))).size shouldBe 1
      await(repository.get(CaseSearch(), Pagination(page = 2, pageSize = 1))).size shouldBe 1
      await(repository.get(CaseSearch(), Pagination(page = 3, pageSize = 1))).size shouldBe 0
    }
  }

  "get by queueId, assigneeId and status" should {

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

    "filter as expected" in {
      val search = CaseSearch(CaseFilter(queueId = queueIdX, assigneeId = Some(assigneeX.id), statuses = Some(Set(PseudoCaseStatus.NEW))))

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

  "generate report" should {

    "group by queue id" in {
      val c1 = aCase(withQueue("queue-1"), withActiveDaysElapsed(1))
      val c2 = aCase(withQueue("queue-1"), withActiveDaysElapsed(2))
      val c3 = aCase(withQueue("queue-2"), withActiveDaysElapsed(3))
      val c4 = aCase(withoutQueue(), withActiveDaysElapsed(4))

      await(repository.insert(c1))
      await(repository.insert(c2))
      await(repository.insert(c3))
      await(repository.insert(c4))
      collectionSize shouldBe 4

      val report = CaseReport(
        filter = CaseReportFilter(),
        group = CaseReportGroup.QUEUE,
        field = CaseReportField.ACTIVE_DAYS_ELAPSED
      )

      val results = await(repository.generateReport(report))
      results should have length 3
      results should contain(ReportResult("queue-1", Seq(1, 2)))
      results should contain(ReportResult("queue-2", Seq(3)))
      results should contain(ReportResult(None, Seq(4)))
    }

    "report on active days elapsed" in {
      val c1 = aCase(withQueue("queue-1"), withActiveDaysElapsed(1))
      val c2 = aCase(withQueue("queue-1"), withActiveDaysElapsed(2))

      await(repository.insert(c1))
      await(repository.insert(c2))
      collectionSize shouldBe 2

      val report = CaseReport(
        filter = CaseReportFilter(),
        group = CaseReportGroup.QUEUE,
        field = CaseReportField.ACTIVE_DAYS_ELAPSED
      )

      val results = await(repository.generateReport(report))
      results should have length 1
      results should contain(ReportResult("queue-1", Seq(1, 2)))
    }

    "report on referred days elapsed" in {
      val c1 = aCase(withQueue("queue-1"), withReferredDaysElapsed(1))
      val c2 = aCase(withQueue("queue-1"), withReferredDaysElapsed(2))

      await(repository.insert(c1))
      await(repository.insert(c2))
      collectionSize shouldBe 2

      val report = CaseReport(
        filter = CaseReportFilter(),
        group = CaseReportGroup.QUEUE,
        field = CaseReportField.REFERRED_DAYS_ELAPSED
      )

      val results = await(repository.generateReport(report))
      results should have length 1
      results should contain(ReportResult("queue-1", Seq(1, 2)))
    }

    "filter on Decision Start Date" in {
      val date = LocalDate.of(2019, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant
      val c1 = aCase(withoutQueue(), withActiveDaysElapsed(1), withDecision(effectiveStartDate = Some(date)))
      val c2 = aCase(withoutQueue(), withActiveDaysElapsed(2), withDecision(effectiveStartDate = Some(date.plusSeconds(1))))

      await(repository.insert(c1))
      await(repository.insert(c2))
      collectionSize shouldBe 2

      val report = CaseReport(
        filter = CaseReportFilter(decisionStartDate = Some(InstantRange(date, date))),
        group = CaseReportGroup.QUEUE,
        field = CaseReportField.ACTIVE_DAYS_ELAPSED
      )

      await(repository.generateReport(report)) shouldBe Seq(ReportResult(None, Seq(1)))
    }

    "filter on status" in {
      val c1 = aCase(withoutQueue(), withActiveDaysElapsed(1), withStatus(CaseStatus.NEW))
      val c2 = aCase(withoutQueue(), withActiveDaysElapsed(2), withStatus(CaseStatus.OPEN))

      await(repository.insert(c1))
      await(repository.insert(c2))
      collectionSize shouldBe 2

      val report = CaseReport(
        filter = CaseReportFilter(status = Some(Set(CaseStatus.NEW))),
        group = CaseReportGroup.QUEUE,
        field = CaseReportField.ACTIVE_DAYS_ELAPSED
      )
      await(repository.generateReport(report)) shouldBe Seq(ReportResult(None, Seq(1)))
    }


    "filter on mulitple statuses" in {
      val c1 = aCase(withoutQueue(), withActiveDaysElapsed(1), withStatus(CaseStatus.NEW))
      val c2 = aCase(withoutQueue(), withActiveDaysElapsed(2), withStatus(CaseStatus.OPEN))

      await(repository.insert(c1))
      await(repository.insert(c2))
      collectionSize shouldBe 2

      val report = CaseReport(
        filter = CaseReportFilter(status = Some(Set(CaseStatus.NEW,CaseStatus.OPEN))),
        group = CaseReportGroup.QUEUE,
        field = CaseReportField.ACTIVE_DAYS_ELAPSED
      )
      await(repository.generateReport(report)) shouldBe Seq(ReportResult(None, Seq(1, 2)))
    }

    "filter on assignee" in {
      val c1 = aCase(withoutQueue(), withActiveDaysElapsed(1), withAssignee(Some(Operator(id="123"))))
      val c2 = aCase(withoutQueue(), withActiveDaysElapsed(2),  withAssignee(Some(Operator(id="456"))))

      await(repository.insert(c1))
      await(repository.insert(c2))
      collectionSize shouldBe 2

      val report = CaseReport(
        filter = CaseReportFilter(assigneeId = Some("123")),
        group = CaseReportGroup.QUEUE,
        field = CaseReportField.ACTIVE_DAYS_ELAPSED
      )
      await(repository.generateReport(report)) shouldBe Seq(ReportResult(None, Seq(1)))
    }


    "filter on Reference" in {
      val c1 = aCase(withoutQueue(), withActiveDaysElapsed(1), withReference("1"))
      val c2 = aCase(withoutQueue(), withActiveDaysElapsed(2), withReference("2"))

      await(repository.insert(c1))
      await(repository.insert(c2))
      collectionSize shouldBe 2

      val report = CaseReport(
        filter = CaseReportFilter(reference = Some(Set("1"))),
        group = CaseReportGroup.QUEUE,
        field = CaseReportField.ACTIVE_DAYS_ELAPSED
      )
      await(repository.generateReport(report)) shouldBe Seq(ReportResult(None, Seq(1)))
    }

    "filter on multiple References" in {
      val c1 = aCase(withoutQueue(), withActiveDaysElapsed(1), withReference("1"))
      val c2 = aCase(withoutQueue(), withActiveDaysElapsed(2), withReference("2"))

      await(repository.insert(c1))
      await(repository.insert(c2))
      collectionSize shouldBe 2

      val report = CaseReport(
        filter = CaseReportFilter(reference = Some(Set("1", "2"))),
        group = CaseReportGroup.QUEUE,
        field = CaseReportField.ACTIVE_DAYS_ELAPSED
      )

      await(repository.generateReport(report)) shouldBe Seq(ReportResult(None, Seq(1, 2)))
    }
  }

  "The 'cases' collection" should {

    "have a unique index based on the field 'reference' " in {
      await(repository.insert(case1))
      val size = collectionSize

      val caught = intercept[DatabaseException] {

        await(repository.insert(case1.copy(status = CaseStatus.REFERRED)))
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

      val repo = newMongoRepository
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
