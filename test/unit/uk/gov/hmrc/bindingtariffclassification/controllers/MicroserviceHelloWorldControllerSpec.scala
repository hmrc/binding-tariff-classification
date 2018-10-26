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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterAll
import org.scalatest.mockito.MockitoSugar
import play.api.http.HeaderNames.{CACHE_CONTROL, LOCATION}
import play.api.http.Status.{BAD_REQUEST, OK}
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtariffclassification.controllers.MicroserviceHelloWorld
import uk.gov.hmrc.bindingtariffclassification.model.search.CaseParamsFilter
import uk.gov.hmrc.bindingtariffclassification.model.{Case, Event}
import uk.gov.hmrc.bindingtariffclassification.service.{CaseService, EventService}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future.successful

class MicroserviceHelloWorldControllerSpec extends UnitSpec
  with WithFakeApplication with MockitoSugar with BeforeAndAfterAll {

  private val mCase = mock[Case]
  private val mEvent = mock[Event]
  private val mockCaseService = mock[CaseService]
  private val mockEventService = mock[EventService]

  private val fakeRequest = FakeRequest()

  private val controller = new MicroserviceHelloWorld(mockCaseService, mockEventService)

  override def beforeAll(): Unit = {
    super.beforeAll()

    when(mockCaseService.insert(any[Case])).thenReturn(successful(mCase))
    when(mockCaseService.update(any[Case])).thenReturn(successful(Some(mCase)))
    when(mockCaseService.get(any[CaseParamsFilter], any[Option[String]])).thenReturn(successful(Seq(mCase)))
    when(mockCaseService.getByReference(any[String])).thenReturn(successful(Some(mCase)))

    when(mockEventService.insert(any[Event])).thenReturn(successful(mEvent))
    when(mockEventService.getById(any[String])).thenReturn(successful(Some(mEvent)))
    when(mockEventService.getByCaseReference(any[String])).thenReturn(successful(List(mEvent)))
  }

  "hello()" should {

    "return 200 when the Location header has a unique value" in {
      val result = controller.hello()(fakeRequest.withHeaders(LOCATION -> "Canary Islands"))
      status(result) shouldBe OK
    }

    "return 400 when the Location header is not sent" in {
      val result = controller.hello()(fakeRequest.withHeaders(CACHE_CONTROL -> "Y"))
      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when the Location header has multiple values" in {
      val headers = Seq(LOCATION -> "Iceland", LOCATION -> "Isle of Man", CACHE_CONTROL -> "Y")
      val result = controller.hello()(fakeRequest.withHeaders(headers: _*))
      status(result) shouldBe BAD_REQUEST
    }

  }

}
