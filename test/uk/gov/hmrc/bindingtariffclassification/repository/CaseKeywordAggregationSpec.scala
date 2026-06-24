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

import org.mockito.ArgumentMatchers.{any, anyInt}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.model.*

import scala.concurrent.{ExecutionContext, Future}

class CaseKeywordAggregationSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val keywordRepo = mock[KeywordsMongoRepository]
  private val viewRepo = mock[CaseKeywordViewMaterializer]

  private val service =
    new CaseKeywordAggregation(keywordRepo, viewRepo)

  private val pagination =
    Pagination(page = 1, pageSize = 10)

  private val rowBike = CaseKeywordViewRow(
    keyword = "bike",
    caseId = "1",
    reference = "REF-1",
    status = "OPEN",
    assignee = Some("001"),
    team = Some("3"),
    goodsName = Some("Bike"),
    caseType = Some("BTI"),
    daysElapsed = 0,
    liabilityStatus = None
  )

  private val rowTool = CaseKeywordViewRow(
    keyword = "tool",
    caseId = "2",
    reference = "REF-2",
    status = "OPEN",
    assignee = Some("002"),
    team = Some("3"),
    goodsName = Some("Tool"),
    caseType = Some("LIABILITY_ORDER"),
    daysElapsed = 0,
    liabilityStatus = Some("LIVE")
  )

  "CaseKeywordAggregation" should {

    "return keywords from cases" in {

      when(keywordRepo.approvedKeywords())
        .thenReturn(Future.successful(Seq.empty))

      when(viewRepo.countRows(any()))
        .thenReturn(Future.successful(2L))

      when(viewRepo.findRows(any(), anyInt(), anyInt()))
        .thenReturn(Future.successful(Seq(rowBike, rowTool)))

      val result =
        service.fetchKeywordsFromCases(pagination).futureValue

      result.resultCount shouldBe 2
      result.results.map(_.keyword.name) should contain theSameElementsAs Seq("bike", "tool")
    }

    "exclude approved keywords" in {

      when(keywordRepo.approvedKeywords())
        .thenReturn(
          Future.successful(
            Seq(
              Keyword(name = "tool", approved = true)
            )
          )
        )

      when(viewRepo.countRows(any()))
        .thenReturn(Future.successful(1L))

      when(viewRepo.findRows(any(), anyInt(), anyInt()))
        .thenReturn(Future.successful(Seq(rowBike)))

      val result =
        service.fetchKeywordsFromCases(pagination).futureValue

      result.resultCount shouldBe 1
      result.results.map(_.keyword.name) shouldBe Seq("bike")
    }
  }
}
