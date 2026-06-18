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
import org.mongodb.scala.bsonDocumentToDocument

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class CaseKeywordMongoViewSpec
    extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with DefaultPlayMongoRepositorySupport[Case] {

  private val config = mock[AppConfig]
  private val view   = new CaseKeywordMongoView(mongoComponent, KeywordCountCache())
  private val repo   = new CaseMongoRepository(config, mongoComponent, new SearchMapper(config), new UpdateMapper)

  override protected val repository: PlayMongoRepository[Case] = repo

  override protected val checkTtlIndex = false

  private val secondsInAYear = 3600 * 24 * 365

  private val btiCaseHeader = CaseHeader(
    reference = "0000001",
    Some(Operator("001", None, None, CLASSIFICATION_OFFICER, List(), List())),
    Some("3"),
    Some("HTC Wildfire smartphone"),
    ApplicationType.BTI,
    CaseStatus.OPEN,
    0,
    None
  )

  private val liabilityCaseHeader = CaseHeader(
    reference = "0000002",
    Some(Operator("002", None, None, CLASSIFICATION_OFFICER, List(), List())),
    Some("3"),
    Some("Hair dryer"),
    ApplicationType.LIABILITY_ORDER,
    CaseStatus.OPEN,
    0,
    Some(LiabilityStatus.LIVE)
  )

  private val caseKeywordBike = CaseKeyword(Keyword("bike"), List(btiCaseHeader, liabilityCaseHeader))
  private val caseKeywordTool = CaseKeyword(Keyword("tool"), List(liabilityCaseHeader))
  private val caseKeywordCar  = CaseKeyword(Keyword("car"), List(liabilityCaseHeader))

  private val caseWithKeywordsBTI: Case =
    Case(
      reference = "0000001",
      status = CaseStatus.OPEN,
      createdDate = Instant.now.minusSeconds(secondsInAYear),
      queueId = Some("3"),
      assignee = Some(Operator("001")),
      application = createBasicBTIApplication,
      decision = Some(createDecision()),
      attachments = Seq.empty,
      keywords = Set(caseKeywordBike.keyword.name)
    )

  private val caseWithKeywordsLiability: Case =
    Case(
      reference = "0000002",
      status = CaseStatus.OPEN,
      createdDate = Instant.now.minusSeconds(1 * secondsInAYear),
      queueId = Some("3"),
      assignee = Some(Operator("002")),
      application = createLiabilityOrder,
      decision = Some(createDecision()),
      attachments = Seq.empty,
      keywords = Set(caseKeywordBike.keyword.name, caseKeywordTool.keyword.name)
    )

  private val caseWithKeywordsLiability2: Case =
    Case(
      reference = "0000003",
      status = CaseStatus.OPEN,
      createdDate = Instant.now.minusSeconds(1 * secondsInAYear),
      queueId = Some("3"),
      assignee = Some(Operator("003")),
      application = createLiabilityOrder,
      decision = Some(createDecision()),
      attachments = Seq.empty,
      keywords = Set(caseKeywordTool.keyword.name, caseKeywordCar.keyword.name)
    )

  override def beforeEach(): Unit = {
    super.beforeEach()
    deleteAll()
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

    "fetchKeywordsFromCases should return keywords from the Cases" in {
      await(repo.insert(caseWithKeywordsBTI))
      await(repo.insert(caseWithKeywordsLiability))

      collectionSize shouldBe 2

      val expected = Seq(caseKeywordBike, caseKeywordTool)

      await(view.fetchKeywordsFromCases(pagination)).results contains expected
    }

    "fetchKeywordsFromCases should return keywords from the Cases and then insert one more" in {
      await(repo.insert(caseWithKeywordsBTI))
      await(repo.insert(caseWithKeywordsLiability))
      collectionSize shouldBe 2
      val expected = Seq(caseKeywordBike, caseKeywordTool)

      await(view.fetchKeywordsFromCases(pagination)).results contains expected

      await(repo.insert(caseWithKeywordsLiability2))
      collectionSize shouldBe 3
      val expected2 = Seq(caseKeywordBike, caseKeywordTool, caseKeywordCar)

      await(view.fetchKeywordsFromCases(pagination)).results contains expected2
    }

    "fetchKeywordsFromCases should return keywords sorted alphabetically" in {
      await(repo.insert(caseWithKeywordsBTI))
      await(repo.insert(caseWithKeywordsLiability))
      await(repo.insert(caseWithKeywordsLiability2))

      collectionSize shouldBe 3

      val result = await(view.fetchKeywordsFromCases(pagination)).results

      val expected = Seq(caseKeywordBike, caseKeywordCar, caseKeywordTool)

      result contains expected
    }

    "fetchKeywordsFromCases should support pagination" in {

      await(repo.insert(caseWithKeywordsBTI))
      await(repo.insert(caseWithKeywordsLiability))
      await(repo.insert(caseWithKeywordsLiability2))

      val firstPage  = await(view.fetchKeywordsFromCases(Pagination(page = 1, pageSize = 2)))
      val secondPage = await(view.fetchKeywordsFromCases(Pagination(page = 2, pageSize = 2)))

      val expectedPage1 = Seq(caseKeywordBike, caseKeywordCar)
      val expectedPage2 = Seq(caseKeywordTool)

      firstPage.results contains expectedPage1
      secondPage.results contains expectedPage2
    }

    "fetchKeywordsFromCases should exclude approved keywords" in {

      await(repo.insert(caseWithKeywordsBTI))
      await(repo.insert(caseWithKeywordsLiability))

      await(
        mongoComponent.database
          .getCollection("keywords")
          .insertOne(
            org.mongodb.scala.bson.BsonDocument(
              "name"     -> org.mongodb.scala.bson.BsonString("tool"),
              "approved" -> org.mongodb.scala.bson.BsonBoolean(true)
            )
          )
          .toFuture()
      )

      val result = await(view.fetchKeywordsFromCases(pagination)).results

      val expected = Seq(caseKeywordBike)

      result contains expected
    }

    "fetchKeywordsFromCases should include keywords with no approval metadata" in {

      await(repo.insert(caseWithKeywordsBTI))

      val result = await(view.fetchKeywordsFromCases(pagination)).results

      val expected = Seq(caseKeywordBike)

      result contains expected
    }

    "fetchKeywordsFromCases should cache the total result count and reuse it on subsequent calls" in {
      await(view.dropView(view.caseKeywordsViewName))
      await(view.createView(view.caseKeywordsViewName, repository.collectionName))

      await(repo.insert(caseWithKeywordsBTI))
      await(repo.insert(caseWithKeywordsLiability))
      collectionSize shouldBe 2

      val firstCallResult = await(view.fetchKeywordsFromCases(pagination))
      val initialCount    = firstCallResult.resultCount

      // caseWithKeywordsBTI has 1 keyword ("bike")
      // caseWithKeywordsLiability has 2 keywords ("bike", "tool")
      // Because your count pipeline runs `unwind("$cases")`, the count matches total keyword references
      initialCount mustBe 3L

      // Insert a new case that would increase the count if the DB were hit directly
      await(repo.insert(caseWithKeywordsLiability2))
      collectionSize shouldBe 3

      // Second call: Should still return the cached total count (3), proving it bypassed recalculation
      val secondCallResult = await(view.fetchKeywordsFromCases(pagination))
      secondCallResult.resultCount mustBe initialCount

      // Verify that the actual data results ARE fresh/updated (because only the count query is cached)
      val firstCallKeywords  = firstCallResult.results.map(_.keyword.name).toSet
      val secondCallKeywords = secondCallResult.results.map(_.keyword.name).toSet

      // The second call data results will include the new keyword ("car")
      secondCallKeywords must contain("car")
      secondCallKeywords.size mustNot be(firstCallKeywords.size)
    }

  }
}
