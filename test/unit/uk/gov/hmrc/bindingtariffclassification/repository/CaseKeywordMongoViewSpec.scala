/*
 * Copyright 2023 HM Revenue & Customs
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

import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.must.Matchers._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.Role.CLASSIFICATION_OFFICER
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.mongo.test.MongoSupport
import util.CaseData.{createBasicBTIApplication, createDecision, createLiabilityOrder}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class CaseKeywordMongoViewSpec
    extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with MongoSupport
    with Eventually {

  private val config     = mock[AppConfig]
  private val repository = newMongoRepository
  private val view       = newMongoAggregation

  private def newMongoRepository: CaseMongoRepository =
    new CaseMongoRepository(config, mongoComponent, new SearchMapper(config), new UpdateMapper)

  private def newMongoAggregation: CaseKeywordMongoView =
    new CaseKeywordMongoView(mongoComponent)

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
      reference   = "0000001",
      status      = CaseStatus.OPEN,
      createdDate = Instant.now.minusSeconds(1 * secondsInAYear),
      queueId     = Some("3"),
      assignee    = Some(Operator("001")),
      application = createBasicBTIApplication,
      decision    = Some(createDecision()),
      attachments = Seq.empty,
      keywords    = Set(caseKeywordBike.keyword.name)
    )

  private val caseWithKeywordsLiability: Case =
    Case(
      reference   = "0000002",
      status      = CaseStatus.OPEN,
      createdDate = Instant.now.minusSeconds(1 * secondsInAYear),
      queueId     = Some("3"),
      assignee    = Some(Operator("002")),
      application = createLiabilityOrder,
      decision    = Some(createDecision()),
      attachments = Seq.empty,
      keywords    = Set(caseKeywordBike.keyword.name, caseKeywordTool.keyword.name)
    )

  private val caseWithKeywordsLiability2: Case =
    Case(
      reference   = "0000003",
      status      = CaseStatus.OPEN,
      createdDate = Instant.now.minusSeconds(1 * secondsInAYear),
      queueId     = Some("3"),
      assignee    = Some(Operator("003")),
      application = createLiabilityOrder,
      decision    = Some(createDecision()),
      attachments = Seq.empty,
      keywords    = Set(caseKeywordTool.keyword.name, caseKeywordCar.keyword.name)
    )

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.deleteAll())
  }

  override def afterAll(): Unit = {
    super.afterAll()
    await(repository.deleteAll())
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
      val result = view.dropView(view.caseKeywordsViewName)

      val futureCollectionNames = await(result).flatMap(_ => mongoComponent.database.listCollectionNames().toFuture())

      await(futureCollectionNames) mustNot contain(view.caseKeywordsViewName)
    }

    "createView will create the view" in {
      val result =
        view.dropView(view.caseKeywordsViewName).map(_ => view.createView(view.caseKeywordsViewName, "cases"))

      val futureCollectionNames = await(result).flatMap(_ => mongoComponent.database.listCollectionNames().toFuture())

      await(futureCollectionNames).sorted mustBe (Seq("system.views", "cases", "caseKeywords").sorted)
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
      await(repository.insert(caseWithKeywordsBTI))
      await(repository.insert(caseWithKeywordsLiability))
      collectionSize shouldBe 2
      val expected = Seq(caseKeywordBike, caseKeywordTool)

      await(view.fetchKeywordsFromCases(pagination)).results contains expected
    }

    "fetchKeywordsFromCases should return keywords from the Cases and then insert one more" in {
      await(repository.insert(caseWithKeywordsBTI))
      await(repository.insert(caseWithKeywordsLiability))
      collectionSize shouldBe 2
      val expected = Seq(caseKeywordBike, caseKeywordTool)

      await(view.fetchKeywordsFromCases(pagination)).results contains expected

      await(repository.insert(caseWithKeywordsLiability2))
      collectionSize shouldBe 3
      val expected2 = Seq(caseKeywordBike, caseKeywordTool, caseKeywordCar)

      await(view.fetchKeywordsFromCases(pagination)).results contains expected2
    }

  }
}
