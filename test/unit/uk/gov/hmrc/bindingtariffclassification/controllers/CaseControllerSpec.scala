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

package uk.gov.hmrc.bindingtariffclassification.controllers

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import play.api.http.HeaderNames.CACHE_CONTROL
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtariffclassification.model.Case
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future
import scala.concurrent.Future.successful

class CaseControllerSpec extends UnitSpec with WithFakeApplication with MockitoSugar {

  private val mCase = mock[Case]
  private val mockCaseService = mock[CaseService]

  private val fakeRequest = FakeRequest("POST", "/cases")

  private val test = new CaseController(mockCaseService)

  "POST /cases" should {

    when(mockCaseService.save(any[Case])).thenReturn(successful((true, mCase)))

    "return 200 when the Location header has a unique value" in {
      val result = test.createCase()(fakeRequest.withBody("test"))
      status(result) shouldBe OK
    }

//    "return 400 when the Location header is not sent" in {
//      val result = controller.createCase()(fakeRequest.withHeaders(CACHE_CONTROL -> "Y"))
//      status(result) shouldBe BAD_REQUEST
//    }

  }

}
