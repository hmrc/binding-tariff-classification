
/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.bindingtariffclassification.repository

import org.mockito.ArgumentMatchers.{any, anyInt}
import org.mockito.Mockito.when
import org.mongodb.scala._
import org.mongodb.scala.bson.conversions.Bson
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.model._

import scala.concurrent.{ExecutionContext, Future}

class CaseKeywordAggregationSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val keywordRepo = mock[KeywordsMongoRepository]
  private val viewUpdater = mock[CaseKeywordViewUpdater]

  private val keywordCollection = mock[MongoCollection[Keyword]]
  private val keywordFindObs    = mock[FindObservable[Keyword]]

  private val viewCollection    = mock[MongoCollection[CaseKeywordViewRow]]
  private val viewFindObs       = mock[FindObservable[CaseKeywordViewRow]]
  private val countObservable   = mock[SingleObservable[java.lang.Long]]

  private val aggregationService = new CaseKeywordAggregation(keywordRepo, viewUpdater)
  private val pagination         = Pagination()

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

  private def stubMockDriver(approvedKeywords: Seq[Keyword], viewRows: Seq[CaseKeywordViewRow], totalCount: Long): Unit = {
    when(keywordRepo.collection).thenReturn(keywordCollection)
    when(keywordCollection.find[Keyword](any[Bson]())(any(), any())).thenReturn(keywordFindObs)
    when(keywordFindObs.toFuture()).thenReturn(Future.successful(approvedKeywords))

    when(viewUpdater.collection).thenReturn(viewCollection)

    when(viewCollection.countDocuments(any[Bson]())(any(), any())).thenReturn(countObservable)
    when(countObservable.toFuture()).thenReturn(Future.successful(java.lang.Long.valueOf(totalCount)))

    when(viewCollection.find[CaseKeywordViewRow](any[Bson]())(any(), any())).thenReturn(viewFindObs)
    when(viewFindObs.sort(any[Bson]())).thenReturn(viewFindObs)
    when(viewFindObs.skip(anyInt())).thenReturn(viewFindObs)
    when(viewFindObs.limit(anyInt())).thenReturn(viewFindObs)
    when(viewFindObs.toFuture()).thenReturn(Future.successful(viewRows))
  }

  "CaseKeywordAggregation" should {

    "fetchKeywordsFromCases should return mapped CaseKeywords from the materialized view" in {
      stubMockDriver(
        approvedKeywords = Seq.empty,
        viewRows = Seq(rowBikeBti, rowToolLiability),
        totalCount = 2
      )

      val result = aggregationService.fetchKeywordsFromCases(pagination).futureValue

      result.resultCount shouldBe 2
      result.results.map(_.keyword.name) should contain theSameElementsAs Seq("bike", "tool")
    }

    "fetchKeywordsFromCases should exclude approved keywords" in {
      stubMockDriver(
        approvedKeywords = Seq(Keyword(name = "tool", approved = true)),
        viewRows = Seq(rowBikeBti),
        totalCount = 1
      )

      val result = aggregationService.fetchKeywordsFromCases(pagination).futureValue

      result.resultCount shouldBe 1
      result.results.map(_.keyword.name) shouldBe Seq("bike")
    }
  }
}
