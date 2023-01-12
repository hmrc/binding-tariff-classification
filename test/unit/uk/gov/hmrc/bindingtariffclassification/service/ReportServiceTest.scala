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

package uk.gov.hmrc.bindingtariffclassification.service

import cats.data.NonEmptySeq
import org.mockito.BDDMockito._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.test.Helpers._
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.reporting._
import uk.gov.hmrc.bindingtariffclassification.repository.{CaseRepository, EventRepository}

import scala.concurrent.Future

class ReportServiceTest extends BaseSpec with BeforeAndAfterEach {

  private val caseRepository = mock[CaseRepository]
  private val service        = new ReportService(caseRepository)

  override def afterEach(): Unit = {
    super.afterEach()
    reset(caseRepository)
  }

  "ReportService.caseReport" should {
    "delegate to case repository" in {
      val report = CaseReport(
        sortBy = ReportField.Reference,
        fields = NonEmptySeq.of(ReportField.Reference, ReportField.Status)
      )

      given(caseRepository.caseReport(report, Pagination())) willReturn Future.successful(
        Paged.empty[Map[String, ReportResultField[_]]]
      )

      await(service.caseReport(report, Pagination())) shouldBe Paged.empty
    }
  }

  "ReportService.summaryReport" should {
    "delegate to case repository" in {
      val report = SummaryReport(
        groupBy = NonEmptySeq.one(ReportField.Status),
        sortBy  = ReportField.Count
      )

      given(caseRepository.summaryReport(report, Pagination())) willReturn Future.successful(Paged.empty[ResultGroup])

      await(service.summaryReport(report, Pagination())) shouldBe Paged.empty
    }
  }

  "ReportService.queueReport" should {
    "delegate to case repository" in {
      val report = QueueReport(
        sortBy = ReportField.Count
      )

      given(caseRepository.queueReport(report, Pagination())) willReturn Future.successful(
        Paged.empty[QueueResultGroup]
      )

      await(service.queueReport(report, Pagination())) shouldBe Paged.empty
    }
  }
}
