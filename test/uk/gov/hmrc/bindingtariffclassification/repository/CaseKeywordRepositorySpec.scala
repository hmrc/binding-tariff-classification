/*
 * Copyright 2025 HM Revenue & Customs
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

import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.model.{IndexOptions, Indexes}
import org.scalatest.matchers.must.Matchers.*
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.*
import uk.gov.hmrc.bindingtariffclassification.model.Role.CLASSIFICATION_OFFICER
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import util.CaseData.{createBasicBTIApplication, createDecision, createLiabilityOrder}

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global

class CaseKeywordRepositorySpec
    extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with DefaultPlayMongoRepositorySupport[Case] {

  private val config      = mock[AppConfig]
  private val caseRepo    = new CaseMongoRepository(config, mongoComponent, new SearchMapper(config), new UpdateMapper)
  private val keywordRepo = new CaseKeywordRepository(mongoComponent)

  override protected val repository = caseRepo

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

  private def caseToHeader(c: Case): CaseHeader =
    CaseHeader(
      reference      = c.reference,
      assignee       = c.assignee,
      team           = c.queueId, // queueId maps to `team` now
      goodsName      = c.application match {
        case bti: BTIApplication => Some(bti.goodName)
        case lo: LiabilityOrder  => lo.goodName
        case corr: CorrespondenceApplication => Some(corr.summary)
        case misc: MiscApplication => Some(misc.name)
      },
      caseType        = c.application.`type`,
      status         = c.status,
      daysElapsed    = c.daysElapsed,
      liabilityStatus = c.application match {
        case lo: LiabilityOrder => Some(lo.status)
        case _                  => None
      }
    )

  override def beforeEach(): Unit = {
    super.beforeEach()

    deleteAll()

    await(
      repository.collection
        .createIndex(Indexes.ascending("createdDate"), IndexOptions().expireAfter(3600 * 24 * 365, TimeUnit.SECONDS))
        .toFuture()
    )
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

  "CaseKeywordRepository" should {

    "refreshKeywords and fetch keywords from the Cases" in {
      await(caseRepo.insert(caseWithKeywordsBTI))
      await(caseRepo.insert(caseWithKeywordsLiability))
      await(keywordRepo.refreshKeywords())

      collectionSize shouldBe 2

      val expected = Seq(caseKeywordBike, caseKeywordTool)
      val actual   = await(keywordRepo.fetchKeywordsFromCases(pagination)).results

      actual must contain theSameElementsAs expected
    }

    "fetchKeywordsFromCases should return updated keywords after adding another case" in {
      await(caseRepo.insert(caseWithKeywordsBTI))
      await(caseRepo.insert(caseWithKeywordsLiability))
      await(keywordRepo.refreshKeywords())

      collectionSize shouldBe 2
      await(keywordRepo.refreshKeywords())

      val expected1 = Seq(caseKeywordBike, caseKeywordTool)
      val actual1   = await(keywordRepo.fetchKeywordsFromCases(pagination)).results
      actual1 must contain theSameElementsAs expected1

      // Insert another case
      await(caseRepo.insert(caseWithKeywordsLiability2))
      await(keywordRepo.refreshKeywords())
      collectionSize shouldBe 3

      val expected2 = Seq(
        caseKeywordBike,
        CaseKeyword(
          Keyword("tool"),
          List(caseToHeader(caseWithKeywordsLiability), caseToHeader(caseWithKeywordsLiability2))
        ),
        CaseKeyword(Keyword("car"), List(caseToHeader(caseWithKeywordsLiability2)))
      )
      val actual2 = await(keywordRepo.fetchKeywordsFromCases(pagination)).results
      actual2 must contain theSameElementsAs expected2
    }

  }
}
