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

import java.util.UUID

import akka.stream.Materializer
import org.mockito.Mockito.when
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.Json.toJson
import play.api.test.FakeRequest
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.Event
import uk.gov.hmrc.bindingtariffclassification.model.JsonFormatters._
import uk.gov.hmrc.bindingtariffclassification.service.EventService
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import util.EventData

import scala.concurrent.Future._

class EventControllerSpec extends UnitSpec with WithFakeApplication with MockitoSugar with Matchers {

  private implicit val mat: Materializer = fakeApplication.materializer

  private val caseReference = UUID.randomUUID().toString
  private val id = UUID.randomUUID().toString

  private val e1: Event = EventData.createCaseStatusChangeEvent(caseReference)
  private val e2: Event = EventData.createNoteEvent(caseReference)

  private val mockEventService = mock[EventService]

  private val fakeRequest = FakeRequest()
  private val appConfig = mock[AppConfig]

  private val controller = new EventController(appConfig, mockEventService)

  "deleteAll()" should {

    "return 403 if the delete mode is disabled" in {

      val result = await(controller.deleteAll()(fakeRequest))

      status(result) shouldEqual FORBIDDEN
      jsonBodyOf(result).toString() shouldEqual """{"code":"FORBIDDEN","message":"You are not allowed to delete."}"""
    }

    "return 204 if the delete mode is enabled" in {
      when(appConfig.isDeleteEnabled).thenReturn(true)
      when(mockEventService.deleteAll).thenReturn(successful(()))

      val result = await(controller.deleteAll()(fakeRequest))

      status(result) shouldEqual NO_CONTENT
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(appConfig.isDeleteEnabled).thenReturn(true)
      when(mockEventService.deleteAll).thenReturn(failed(error))

      val result = await(controller.deleteAll()(fakeRequest))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
      jsonBodyOf(result).toString() shouldEqual """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

//  "getById()" should {
//
//    "return 200 with the expected event" in {
//      when(mockEventService.getById(id)).thenReturn(successful(Some(e1)))
//
//      val result = await(controller.getById(id)(fakeRequest))
//
//      status(result) shouldEqual OK
//      jsonBodyOf(result) shouldEqual toJson(Some(e1))
//    }
//
//    "return 404 if there are no events for the specific id" in {
//      when(mockEventService.getById(id)).thenReturn(successful(None))
//
//      val result = await(controller.getById(id)(fakeRequest))
//
//      status(result) shouldEqual NOT_FOUND
//      jsonBodyOf(result).toString() shouldEqual """{"code":"NOT_FOUND","message":"Event not found"}"""
//    }
//
//    "return 500 when an error occurred" in {
//      val error = new RuntimeException
//
//      when(mockEventService.getById(id)).thenReturn(failed(error))
//
//      val result = await(controller.getById(id)(fakeRequest))
//
//      status(result) shouldEqual INTERNAL_SERVER_ERROR
//      jsonBodyOf(result).toString() shouldEqual """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
//    }
//
//  }

  "getByCaseReference()" should {

    "return 200 with the expected events" in {
      when(mockEventService.getByCaseReference(caseReference)).thenReturn(successful(Seq(e1, e2)))

      val result = await(controller.getByCaseReference(caseReference)(fakeRequest))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual toJson(Seq(e1, e2))
    }

    "return 200 with an empty sequence when there are no events for a specific case" in {
      when(mockEventService.getByCaseReference(caseReference)).thenReturn(successful(Seq.empty))

      val result = await(controller.getByCaseReference(caseReference)(fakeRequest))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual toJson(Seq.empty[Event])
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(mockEventService.getByCaseReference(caseReference)).thenReturn(failed(error))

      val result = await(controller.getByCaseReference(caseReference)(fakeRequest))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
      jsonBodyOf(result).toString() shouldEqual """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

}
