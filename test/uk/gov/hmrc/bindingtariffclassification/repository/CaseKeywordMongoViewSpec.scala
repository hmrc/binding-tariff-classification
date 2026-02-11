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

import org.scalatest.matchers.must.Matchers._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.Role.CLASSIFICATION_OFFICER
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import util.CaseData.{createBasicBTIApplication, createDecision, createLiabilityOrder}
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.ObservableFuture

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class CaseKeywordMongoViewSpec
    extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with DefaultPlayMongoRepositorySupport[Case] {

  private val config = mock[AppConfig]
  private val view   = new CaseKeywordMongoView(mongoComponent)
  private val repo   = new CaseMongoRepository(config, mongoComponent, new SearchMapper(config), new UpdateMapper)

  override protected val repository: PlayMongoRepository[Case] = repo

  override protected val checkTtlIndex = false

  private val secondsInAYear = 3600 * 24 * 365

  private val caseWithKeywordsBTI: Case =
    Case(
      reference = "0000001",
      status = CaseStatus.OPEN,
      createdDate = Instant.now.minusSeconds(secondsInAYear),
      queueId = Some("3"),
      assignee = Some(Operator("001", name = Some("001"), Some("Officer 1"), CLASSIFICATION_OFFICER, List(), List())),
      application = createBasicBTIApplication.copy(goodName = "HTC Wildfire smartphone"),
      decision = Some(createDecision()),
      attachments = Seq.empty,
      keywords = Set("bike"),
      daysElapsed = 365
    )

  private val caseWithKeywordsLiability: Case =
    Case(
      reference = "0000002",
      status = CaseStatus.OPEN,
      createdDate = Instant.now.minusSeconds(1 * secondsInAYear),
      queueId = Some("3"),
      assignee = Some(Operator("002", None, Some("Officer 2"), CLASSIFICATION_OFFICER, List(), List())),
      application = createLiabilityOrder.copy(goodName = Some("Hair dryer")),
      decision = Some(createDecision()),
      attachments = Seq.empty,
      keywords = Set("bike", "tool")
    )

  private val caseWithKeywordsLiability2: Case =
    Case(
      reference = "0000003",
      status = CaseStatus.OPEN,
      createdDate = Instant.now.minusSeconds(1 * secondsInAYear),
      queueId = Some("3"),
      assignee = Some(Operator("003", None, Some("Officer 3"), CLASSIFICATION_OFFICER, List(), List())),
      application = createLiabilityOrder.copy(goodName = Some("Car parts")),
      decision = Some(createDecision()),
      attachments = Seq.empty,
      keywords = Set("tool", "car")
    )

  override def beforeEach(): Unit = {
    super.beforeEach()
    deleteAll()
    await(view.initView)
    ()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    deleteAll()
  }

  private def collectionSize: Int =
    await(
      repository.collection
        .countDocuments()
        .toFuture()
        .map(_.toInt)
    )

  private val pagination = Pagination()

  "CaseKeywordMongoView" should {

    "dropView will drop the view" in {
      val result                = view.dropView(view.caseKeywordsViewName)
      val futureCollectionNames = await(result).flatMap(_ => mongoComponent.database.listCollectionNames().toFuture())

      await(futureCollectionNames) mustNot contain(view.caseKeywordsViewName)
    }

    "createView will create the view" in {
      val result =
        view.dropView(view.caseKeywordsViewName).map(_ => view.createView(view.caseKeywordsViewName, "cases"))

      val futureCollectionNames = await(result).flatMap(_ => mongoComponent.database.listCollectionNames().toFuture())

      await(futureCollectionNames).toSeq.sorted mustBe Seq("system.views", "cases", "caseKeywords").sorted
    }

    "getView will get the view" in {
      val result = view
        .dropView(view.caseKeywordsViewName)
        .map(_ => view.initView)
        .map(_ =>
          view
            .createView(view.caseKeywordsViewName, "cases")
            .flatMap(_ => view.getView(view.caseKeywordsViewName).countDocuments().head())
        )

      val futureViewCount = await(result)
      await(futureViewCount) mustBe 0
    }

    "fetchKeywordsFromCases should return flat keyword rows" in {
      await(repo.insert(caseWithKeywordsBTI))
      await(repo.insert(caseWithKeywordsLiability))

      collectionSize shouldBe 2

      val result = await(view.fetchKeywordsFromCases(pagination))

      result.resultCount shouldBe 3

      val keywords = result.results.map(_.keyword).toSet
      keywords shouldBe Set("bike", "tool")

      result.results.count(_.keyword == "bike") shouldBe 2

      val references = result.results.map(_.reference).toSet
      references shouldBe Set("0000001", "0000002")
    }

    "fetchKeywordsFromCases should handle pagination correctly" in {
      await(repo.insert(caseWithKeywordsBTI))
      await(repo.insert(caseWithKeywordsLiability))
      await(repo.insert(caseWithKeywordsLiability2))

      collectionSize shouldBe 3

      val page1 = await(view.fetchKeywordsFromCases(Pagination(page = 1, pageSize = 2)))
      page1.results.size shouldBe 2
      page1.resultCount  shouldBe 5

      val page2 = await(view.fetchKeywordsFromCases(Pagination(page = 2, pageSize = 2)))
      page2.results.size shouldBe 2
      page2.resultCount  shouldBe 5

      val page3 = await(view.fetchKeywordsFromCases(Pagination(page = 3, pageSize = 2)))
      page3.results.size shouldBe 1
      page3.resultCount  shouldBe 5
    }

    "fetchKeywordsFromCases should return correct structure for flat rows" in {
      await(repo.insert(caseWithKeywordsBTI))

      val result = await(view.fetchKeywordsFromCases(pagination))
      val row    = result.results.head

      row.keyword   shouldBe "bike"
      row.reference shouldBe "0000001"
      row.user      shouldBe Some("001")
      row.goods     shouldBe Some("HTC Wildfire smartphone")
      row.caseType  shouldBe ApplicationType.BTI
      row.status    shouldBe CaseStatus.OPEN
    }
  }
}
