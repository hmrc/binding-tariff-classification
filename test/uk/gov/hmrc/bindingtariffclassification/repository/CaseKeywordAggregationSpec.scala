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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mongodb.scala._
import org.mongodb.scala.bson.conversions.Bson
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.model._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CaseKeywordAggregationSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures {

  private val keywordRepo = mock[KeywordsMongoRepository]
  private val viewUpdater = mock[CaseKeywordViewUpdater]

  private val collection = mock[MongoCollection[CaseKeywordViewRow]]
  private val findObs    = mock[FindObservable[CaseKeywordViewRow]]

  private val aggregationService =
    new CaseKeywordAggregation(keywordRepo, viewUpdater)

  private val pagination = Pagination()

  private val rowBikeBti = CaseKeywordViewRow(
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

  private val rowToolLiability = CaseKeywordViewRow(
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

  "CaseKeywordAggregation" should {

    "fetchKeywordsFromCases should return mapped CaseKeywords from the materialized view" in {

      when(keywordRepo.approvedKeywords())
        .thenReturn(Future.successful(Seq.empty))

      when(viewUpdater.collection)
        .thenReturn(collection)

      when(collection.countDocuments(any[Bson]))
        .thenReturn(SingleObservable(2L))

      when(collection.find(any[Bson]))
        .thenReturn(findObs)

      when(findObs.sort(any[Bson]))
        .thenReturn(findObs)

      when(findObs.skip(any[Int]))
        .thenReturn(findObs)

      when(findObs.limit(any[Int]))
        .thenReturn(findObs)

      when(findObs.toFuture())
        .thenReturn(
          Future.successful(
            Seq(rowBikeBti, rowToolLiability)
          )
        )

      val result =
        aggregationService
          .fetchKeywordsFromCases(pagination)
          .futureValue

      result.resultCount shouldBe 2

      result.results.map(_.keyword.name) should contain theSameElementsAs
        Seq("bike", "tool")
    }

    "fetchKeywordsFromCases should exclude approved keywords" in {

      when(keywordRepo.approvedKeywords())
        .thenReturn(
          Future.successful(
            Seq(Keyword("tool", approved = true))
          )
        )

      when(viewUpdater.collection)
        .thenReturn(collection)

      when(collection.countDocuments(any[Bson]))
        .thenReturn(SingleObservable(1L))

      when(collection.find(any[Bson]))
        .thenReturn(findObs)

      when(findObs.sort(any[Bson]))
        .thenReturn(findObs)

      when(findObs.skip(any[Int]))
        .thenReturn(findObs)

      when(findObs.limit(any[Int]))
        .thenReturn(findObs)

      when(findObs.toFuture())
        .thenReturn(
          Future.successful(
            Seq(rowBikeBti)
          )
        )

      val result =
        aggregationService
          .fetchKeywordsFromCases(pagination)
          .futureValue

      result.resultCount shouldBe 1
      result.results.map(_.keyword.name) shouldBe Seq("bike")
    }
  }
}
