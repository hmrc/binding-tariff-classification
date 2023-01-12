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

package uk.gov.hmrc.bindingtariffclassification.controllers

import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status._
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.model.reporting._
import uk.gov.hmrc.bindingtariffclassification.service.ReportService

import scala.concurrent.Future
import uk.gov.hmrc.bindingtariffclassification.model.Pagination
import uk.gov.hmrc.bindingtariffclassification.model.Paged

class ReportingControllerSpec extends BaseSpec with BeforeAndAfterEach {

  private val reportService = mock[ReportService]

  private val fakeRequest = FakeRequest()
  private val controller  = new ReportingController(reportService, mcc)

  override protected def afterEach(): Unit = {
    super.afterEach()
    Mockito.reset(reportService)
  }

  "GET summary report" should {
    "delegate to service" in {
      val report = mock[SummaryReport]

      given(reportService.summaryReport(report, Pagination())) willReturn Future.successful(Paged.empty[ResultGroup])

      val result = controller.summaryReport(report, Pagination())(fakeRequest).futureValue

      result.header.status shouldBe OK
    }
  }

  "GET case report" should {
    "delegate to service" in {
      val report = mock[CaseReport]

      given(reportService.caseReport(report, Pagination())) willReturn Future.successful(
        Paged.empty[Map[String, ReportResultField[_]]]
      )

      val result = controller.caseReport(report, Pagination())(fakeRequest).futureValue

      result.header.status shouldBe OK
    }
  }

  "GET queue report" should {
    "delegate to service" in {
      val report = mock[QueueReport]

      given(reportService.queueReport(report, Pagination())) willReturn Future.successful(Paged.empty[QueueResultGroup])

      val result = controller.queueReport(report, Pagination())(fakeRequest).futureValue

      result.header.status shouldBe OK
    }
  }
}
