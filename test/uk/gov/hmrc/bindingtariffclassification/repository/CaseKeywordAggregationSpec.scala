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

import org.mockito.Mockito.when
import org.mongodb.scala.SingleObservableFuture
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.*
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CaseKeywordAggregationSpec
    extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with MockitoSugar
    with DefaultPlayMongoRepositorySupport[CaseKeywordViewRow] {

  private val config = mock[AppConfig]

  private val keywordRepo =
    mock[KeywordsMongoRepository]

  private val repo =
    new CaseMongoRepository(
      config,
      mongoComponent,
      new SearchMapper(config),
      new UpdateMapper
    )

  private val viewUpdater =
    new CaseKeywordViewUpdater(
      mongoComponent,
      config,
      repo
    )

  private val aggregationService =
    new CaseKeywordAggregation(
      keywordRepo,
      viewUpdater
    )

  override protected val repository: PlayMongoRepository[CaseKeywordViewRow] =
    viewUpdater

  override protected val checkTtlIndex = false

  private val rowBikeBti =
    CaseKeywordViewRow(
      keyword = "bike",
      caseId = "0000001",
      reference = "0000001",
      status = "OPEN",
      assignee = Some("001"),
      team = Some("3"),
      goodsName = Some("HTC Wildfire smartphone"),
      caseType = Some("BTI"),
      daysElapsed = 0,
      liabilityStatus = None
    )

  private val rowToolLiability =
    CaseKeywordViewRow(
      keyword = "tool",
      caseId = "0000002",
      reference = "0000002",
      status = "OPEN",
      assignee = Some("002"),
      team = Some("3"),
      goodsName = Some("Hair dryer"),
      caseType = Some("LIABILITY_ORDER"),
      daysElapsed = 0,
      liabilityStatus = Some("LIVE")
    )

  override def beforeEach(): Unit = {
    super.beforeEach()

    deleteAll()

    when(keywordRepo.approvedKeywords())
      .thenReturn(Future.successful(Seq.empty))
  }

  private val pagination = Pagination()

  "CaseKeywordAggregation" should {

    "fetchKeywordsFromCases should return mapped CaseKeywords from the materialized view" in {

      await(viewUpdater.collection.insertOne(rowBikeBti).toFuture())
      await(viewUpdater.collection.insertOne(rowToolLiability).toFuture())

      val pagedResult =
        await(
          aggregationService.fetchKeywordsFromCases(
            pagination
          )
        )

      pagedResult.resultCount shouldBe 2

      pagedResult.results.map(_.keyword.name) should contain theSameElementsAs
        Seq("bike", "tool")
    }

    "fetchKeywordsFromCases should exclude keywords that are marked as approved" in {

      await(viewUpdater.collection.insertOne(rowBikeBti).toFuture())
      await(viewUpdater.collection.insertOne(rowToolLiability).toFuture())

      when(keywordRepo.approvedKeywords())
        .thenReturn(
          Future.successful(
            Seq(
              Keyword(
                name = "tool",
                approved = true
              )
            )
          )
        )

      val pagedResult =
        await(
          aggregationService.fetchKeywordsFromCases(
            pagination
          )
        )

      pagedResult.resultCount shouldBe 1

      pagedResult.results.map(_.keyword.name) shouldBe Seq(
        "bike"
      )
    }
  }
}
