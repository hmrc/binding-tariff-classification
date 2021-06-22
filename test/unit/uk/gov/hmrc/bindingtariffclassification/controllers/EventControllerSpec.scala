/*
 * Copyright 2021 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.refEq
import org.mockito.Mockito.{verifyNoMoreInteractions, when}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.Json.toJson
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.{CaseService, EventService}
import uk.gov.hmrc.http.HttpVerbs
import util.EventData

import java.time.Instant
import java.util.UUID
import scala.concurrent.Future._

class EventControllerSpec extends BaseSpec with BeforeAndAfterEach {

  private val caseReference = UUID.randomUUID().toString

  private val e1: Event = EventData.createCaseStatusChangeEvent(caseReference)
  private val e2: Event = EventData.createNoteEvent(caseReference)

  private val eventService = mock[EventService]
  private val casesService = mock[CaseService]

  private val fakeRequest = FakeRequest()
  private val appConfig   = mock[AppConfig]

  private val controller = new EventController(appConfig, eventService, casesService, parser, mcc)

  override protected def afterEach(): Unit = {
    super.afterEach()
    Mockito.reset(casesService, eventService)
  }

  "deleteAll()" should {

    val req = FakeRequest(method = HttpVerbs.DELETE, path = "/events")

    "return 403 if the test mode is disabled" in {
      when(appConfig.isTestMode).thenReturn(false)
      val result = controller.deleteAll()(req)

      status(result) shouldBe FORBIDDEN
      contentAsJson(result)
        .toString() shouldBe s"""{"code":"FORBIDDEN","message":"You are not allowed to call ${req.method} ${req.path}"}"""
    }

    "return 204 if the test mode is enabled" in {
      when(appConfig.isTestMode).thenReturn(true)
      when(eventService.deleteAll()).thenReturn(successful(()))

      val result = controller.deleteAll()(req).futureValue

      result.header.status shouldBe NO_CONTENT
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(appConfig.isTestMode).thenReturn(true)
      when(eventService.deleteAll()).thenReturn(failed(error))

      val result = controller.deleteAll()(req)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "deleteCaseEvents()" should {

    val req = FakeRequest(method = HttpVerbs.DELETE, path = "/events/ref")

    "return 403 if the test mode is disabled" in {
      when(appConfig.isTestMode).thenReturn(false)
      val result = controller.deleteCaseEvents("ref")(req)

      status(result) shouldBe FORBIDDEN
      contentAsJson(result)
        .toString() shouldBe s"""{"code":"FORBIDDEN","message":"You are not allowed to call ${req.method} ${req.path}"}"""
    }

    "return 204 if the test mode is enabled" in {
      when(appConfig.isTestMode).thenReturn(true)
      when(eventService.deleteCaseEvents(refEq("ref"))).thenReturn(successful(()))

      val result = controller.deleteCaseEvents("ref")(req).futureValue

      result.header.status shouldBe NO_CONTENT
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(appConfig.isTestMode).thenReturn(true)
      when(eventService.deleteCaseEvents(refEq("ref"))).thenReturn(failed(error))

      val result = controller.deleteCaseEvents("ref")(req)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "getByCaseReference()" should {

    "return 200 with the expected events" in {
      when(eventService.search(EventSearch(Some(Set(caseReference))), Pagination()))
        .thenReturn(successful(Paged(Seq(e1, e2))))

      val result = controller.getByCaseReference(caseReference, Pagination())(fakeRequest)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe toJson(Paged(Seq(e1, e2)))
    }

    "return 200 with an empty sequence when there are no events for a specific case" in {
      when(eventService.search(EventSearch(Some(Set(caseReference))), Pagination()))
        .thenReturn(successful(Paged.empty[Event]))

      val result = controller.getByCaseReference(caseReference, Pagination())(fakeRequest)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe toJson(Paged.empty[Event])
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(eventService.search(EventSearch(Some(Set(caseReference))), Pagination())).thenReturn(failed(error))

      val result = controller.getByCaseReference(caseReference, Pagination())(fakeRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }
  }

  "search()" should {

    "return 200 with the expected events" in {
      when(eventService.search(EventSearch(), Pagination())).thenReturn(successful(Paged(Seq(e1, e2))))

      val result = controller.search(EventSearch(), Pagination())(fakeRequest)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe toJson(Paged(Seq(e1, e2)))
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(eventService.search(EventSearch(), Pagination())).thenReturn(failed(error))

      val result = controller.search(EventSearch(), Pagination())(fakeRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }
  }

  "create" should {
    val note      = Note("note")
    val timestamp = Instant.EPOCH
    val userId    = "user-id"
    val newEvent  = NewEventRequest(note, Operator(userId, Some("user name")), timestamp)
    val event = Event(
      id      = "id",
      details = note,
      Operator(userId, Some("user name")),
      caseReference = caseReference,
      timestamp     = timestamp
    )

    "return 201 Created" in {
      val aCase = mock[Case]
      when(aCase.reference).thenReturn(caseReference)
      when(casesService.getByReference(caseReference)).thenReturn(successful(Some(aCase)))
      when(eventService.insert(ArgumentMatchers.any[Event])).thenReturn(successful(event))

      val request        = fakeRequest.withBody(toJson(newEvent))
      val result = controller.create(caseReference)(request)

      status(result) shouldBe CREATED
      contentAsJson(result) shouldBe toJson(event)
    }

    "return 404 Not Found for invalid Reference" in {
      when(casesService.getByReference(caseReference)).thenReturn(successful(None))

      val request        = fakeRequest.withBody(toJson(newEvent))
      val result = controller.create(caseReference)(request)

      verifyNoMoreInteractions(eventService)
      status(result) shouldBe NOT_FOUND
      contentAsJson(result).toString() shouldBe "{\"code\":\"NOT_FOUND\",\"message\":\"Case not found\"}"
    }
  }

}
