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

import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.{SingleObservableFuture, bsonDocumentToDocument}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.*
import uk.gov.hmrc.bindingtariffclassification.model.Role.CLASSIFICATION_OFFICER
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import util.CaseData.{createBasicBTIApplication, createDecision, createLiabilityOrder}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class CaseKeywordAggregationSpec
    extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with MockitoSugar
    with DefaultPlayMongoRepositorySupport[CaseKeywordViewRow] {

  private val config      = mock[AppConfig]
  private val keywordRepo = new KeywordsMongoRepository(mongoComponent, config)

  private val repo        = new CaseMongoRepository(config, mongoComponent, new SearchMapper(config), new UpdateMapper)
  private val viewUpdater = new CaseKeywordViewUpdater(mongoComponent, config, repo)

  private val aggregationService = new CaseKeywordAggregation(keywordRepo, viewUpdater)

  override protected val repository: PlayMongoRepository[CaseKeywordViewRow] = viewUpdater

  override protected val checkTtlIndex = false

  private val rowBikeBti = CaseKeywordViewRow(
    caseId = "0000001_bike",
    keyword = "bike",
    reference = "0000001",
    status = "OPEN",
    assignee = Some("001"),
    team = Some("3"),
    goodsName = Some("HTC Wildfire smartphone"),
    caseType = Some("BTI"),
    daysElapsed = 0,
    liabilityStatus = None
  )

  private val rowBikeLiability = CaseKeywordViewRow(
    caseId = "0000002_bike",
    keyword = "bike",
    reference = "0000002",
    status = "OPEN",
    assignee = Some("002"),
    team = Some("3"),
    goodsName = Some("Hair dryer"),
    caseType = Some("LIABILITY_ORDER"),
    daysElapsed = 0,
    liabilityStatus = Some("LIVE")
  )

  private val rowToolLiability = CaseKeywordViewRow(
    caseId = "0000002_tool",
    keyword = "tool",
    reference = "0000002",
    status = "OPEN",
    assignee = Some("002"),
    team = Some("3"),
    goodsName = Some("Hair dryer"),
    caseType = Some("LIABILITY_ORDER"),
    daysElapsed = 0,
    liabilityStatus = Some("LIVE")
  )

  private val rowCarLiability = CaseKeywordViewRow(
    caseId = "0000003_car",
    keyword = "car",
    reference = "0000003",
    status = "OPEN",
    assignee = Some("003"),
    team = Some("3"),
    goodsName = Some("Hair dryer"),
    caseType = Some("LIABILITY_ORDER"),
    daysElapsed = 0,
    liabilityStatus = Some("LIVE")
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    deleteAll()
    await(keywordRepo.collection.drop().toFuture())
    await(
      mongoComponent.database
        .getCollection("keywords")
        .createIndex(ascending("approved"))
        .toFuture()
    )
  }

  override def afterAll(): Unit = {
    super.afterAll()
    deleteAll()
  }

  private val pagination = Pagination()

  "CaseKeywordAggregation" should {

    "fetchKeywordsFromCases should return mapped CaseKeywords from the materialized view" in {
      await(viewUpdater.collection.insertOne(rowBikeBti).toFuture())
      await(viewUpdater.collection.insertOne(rowToolLiability).toFuture())

      val pagedResult = await(aggregationService.fetchKeywordsFromCases(pagination))

      pagedResult.resultCount                             shouldBe 2
      pagedResult.results.map(_.keyword.name)               should contain theSameElementsAs Seq("bike", "tool")
      pagedResult.results.flatMap(_.cases.map(_.reference)) should contain theSameElementsAs Seq("0000001", "0000002")
    }

    "fetchKeywordsFromCases should support pagination correctly" in {
      await(viewUpdater.collection.insertOne(rowBikeBti).toFuture())
      await(viewUpdater.collection.insertOne(rowCarLiability).toFuture())
      await(viewUpdater.collection.insertOne(rowToolLiability).toFuture())

      val page1 = await(aggregationService.fetchKeywordsFromCases(Pagination(page = 1, pageSize = 2)))
      val page2 = await(aggregationService.fetchKeywordsFromCases(Pagination(page = 2, pageSize = 2)))

      page1.results.map(_.keyword.name) shouldBe Seq("bike", "car")
      page2.results.map(_.keyword.name) shouldBe Seq("tool")

      page1.resultCount shouldBe 3
      page2.resultCount shouldBe 3
    }

    "fetchKeywordsFromCases should exclude keywords that are marked as approved" in {
      await(viewUpdater.collection.insertOne(rowBikeBti).toFuture())
      await(viewUpdater.collection.insertOne(rowToolLiability).toFuture())

      await(keywordRepo.insert(Keyword(name = "tool", approved = true)))

      val pagedResult = await(aggregationService.fetchKeywordsFromCases(pagination))

      pagedResult.resultCount                 shouldBe 1
      pagedResult.results.map(_.keyword.name) shouldBe Seq("bike")
    }

    "fetchKeywordsFromCases should include all keywords if none are approved" in {
      await(viewUpdater.collection.insertOne(rowBikeBti).toFuture())

      await(keywordRepo.insert(Keyword(name = "bike")))

      val pagedResult = await(aggregationService.fetchKeywordsFromCases(pagination))

      pagedResult.resultCount                 shouldBe 1
      pagedResult.results.map(_.keyword.name) shouldBe Seq("bike")
    }
  }
}
