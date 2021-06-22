/*
 * Copyright 2021 HM Revenue & Customs
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
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.test.Helpers. _
import reactivemongo.api.{DB, ReadConcern}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.Role.CLASSIFICATION_OFFICER
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.mongo.MongoSpecSupport
import util.CaseData.{createBasicBTIApplication, createDecision, createLiabilityOrder}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class CaseKeywordMongoViewSpec
    extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with MongoSpecSupport
    with Eventually {
  self =>

  private val mongoDbProvider: MongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private val config     = mock[AppConfig]
  private val repository = newMongoRepository
  private val view       = newMongoAggregation

  private def newMongoRepository: CaseMongoRepository =
    new CaseMongoRepository(config, mongoDbProvider, new SearchMapper(config), new UpdateMapper)

  private def newMongoAggregation: CaseKeywordMongoView =
    new CaseKeywordMongoView(mongoDbProvider)

  private val secondsInAYear = 3600 * 24 * 365

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
      keywords    = Set("bike")
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
      keywords    = Set("bike", "tool")
    )

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

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    await(repository.drop)
  }

  private def collectionSize: Int =
    await(
      repository.collection
        .count(selector = None, limit = Some(0), skip = 0, hint = None, readConcern = ReadConcern.Local)
    ).toInt

  private val pagination = Pagination()

  "fetchKeywordsFromCases" should {

    "return keywords from the Cases" in {
      await(repository.insert(caseWithKeywordsBTI))
      await(repository.insert(caseWithKeywordsLiability))
      collectionSize shouldBe 2
      val expected = List(caseKeywordBike, caseKeywordTool)

      await(view.fetchKeywordsFromCases(pagination)).results contains expected
    }
  }
}
