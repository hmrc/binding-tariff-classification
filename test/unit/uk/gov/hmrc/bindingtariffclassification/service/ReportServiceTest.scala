/*
 * Copyright 2019 HM Revenue & Customs
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

import org.mockito.BDDMockito._
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.model.reporting.{CaseReport, ReportResult}
import uk.gov.hmrc.bindingtariffclassification.repository.CaseRepository
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class ReportServiceTest extends UnitSpec with MockitoSugar {


  private val repository = mock[CaseRepository]
  private val service = new ReportService(repository)

  "Service  'Get Report'" should {
    "Delegate to Repository" in {
      val report = mock[CaseReport]

      given(repository.generateReport(report)) willReturn Future.successful(Seq.empty[ReportResult])

      await(service.generate(report)) shouldBe Seq.empty
    }
  }

}
