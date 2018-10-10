/*
 * Copyright 2018 HM Revenue & Customs
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

package unit.uk.gov.hmrc.bindingtariffclassification.controllers

import akka.stream.Materializer
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status.{BAD_REQUEST, CREATED}
import play.api.libs.json.Json.toJson
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtariffclassification.controllers.CaseController
import uk.gov.hmrc.bindingtariffclassification.model.Case
import uk.gov.hmrc.bindingtariffclassification.model.JsonFormatters._
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.bindingtariffclassification.todelete.CaseData
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future.successful

class CaseControllerSpec extends UnitSpec with WithFakeApplication with MockitoSugar {

  private implicit val mat: Materializer = fakeApplication.materializer

  private val c: Case = CaseData.createCase()
  private val mockCaseService = mock[CaseService]

  private val fakeRequest = FakeRequest("POST", "/cases")

  private val controller = new CaseController(mockCaseService)

  "POST /cases" should {

    when(mockCaseService.save(c)).thenReturn(successful((true, c)))

    "return 201 when the case has been created successfully" in {
      val result = await(controller.createCase()(fakeRequest.withBody(toJson(c))))

      status(result) shouldEqual CREATED
      jsonBodyOf(result) shouldEqual toJson(c)
    }

    // TODO: add more scenarios
  }

}
