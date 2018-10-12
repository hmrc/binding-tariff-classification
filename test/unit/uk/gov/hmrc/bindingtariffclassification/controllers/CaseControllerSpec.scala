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
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status.{BAD_REQUEST, CREATED, INTERNAL_SERVER_ERROR}
import play.api.libs.json.Json.toJson
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtariffclassification.controllers.CaseController
import uk.gov.hmrc.bindingtariffclassification.model.Case
import uk.gov.hmrc.bindingtariffclassification.model.JsonFormatters._
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.bindingtariffclassification.todelete.CaseData
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future._

class CaseControllerSpec extends UnitSpec with WithFakeApplication with MockitoSugar {

  private implicit val mat: Materializer = fakeApplication.materializer

  private val c: Case = CaseData.createCase()
  private val mockCaseService = mock[CaseService]

  private val fakeRequest = FakeRequest("POST", "/cases")

  private val controller = new CaseController(mockCaseService)

  "POST /cases" should {


    "return 201 when the case has been created successfully" in {
      when(mockCaseService.insert(c)).thenReturn(successful((c)))

      val result = await(controller.createCase()(fakeRequest.withBody(toJson(c))))

      status(result) shouldEqual CREATED
      jsonBodyOf(result) shouldEqual toJson(c)
    }


    "return 400 when the case json does not match with the object" in {
      val body = """{"a":"b"}"""
      val result = await(controller.createCase()(fakeRequest.withBody(toJson(body))))

      status(result) shouldEqual BAD_REQUEST
    }


    "return 500 when the case json does not match with the object" in {
      when(mockCaseService.insert(c)).thenReturn(failed(new RuntimeException("runtime-test-exception")))

      val result = await(controller.createCase()(fakeRequest.withBody(toJson(c))))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
    }

  }
}
