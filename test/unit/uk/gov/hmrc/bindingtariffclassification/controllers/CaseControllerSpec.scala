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

package uk.gov.hmrc.bindingtariffclassification.controllers

import akka.stream.Materializer
import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.Mockito.when
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.Json.toJson
import play.api.test.FakeRequest
import reactivemongo.bson.BSONDocument
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters._
import uk.gov.hmrc.bindingtariffclassification.model.search.{Filter, Search, Sort}
import uk.gov.hmrc.bindingtariffclassification.model.sort.SortField
import uk.gov.hmrc.bindingtariffclassification.model.{Case, NewCaseRequest}
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.http.HttpVerbs
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import util.CaseData

import scala.concurrent.Future._

class CaseControllerSpec extends UnitSpec with WithFakeApplication with MockitoSugar with Matchers {

  private implicit val mat: Materializer = fakeApplication.materializer

  private val newCase: NewCaseRequest = CaseData.createNewCase()
  private val c1: Case = CaseData.createCase()
  private val c2: Case = CaseData.createCase()

  private val caseService = mock[CaseService]
  private val appConfig = mock[AppConfig]

  private val fakeRequest = FakeRequest()

  private val controller = new CaseController(appConfig, caseService)

  "deleteAll()" should {

    val req = FakeRequest(method = HttpVerbs.DELETE, path = "/cases")

    "return 403 if the test mode is disabled" in {

      val result = await(controller.deleteAll()(req))

      status(result) shouldEqual FORBIDDEN
      jsonBodyOf(result).toString() shouldEqual s"""{"code":"FORBIDDEN","message":"You are not allowed to call ${req.method} ${req.path}"}"""
    }

    "return 204 if the test mode is enabled" in {
      when(appConfig.isTestMode).thenReturn(true)
      when(caseService.deleteAll()).thenReturn(successful(()))

      val result = await(controller.deleteAll()(req))

      status(result) shouldEqual NO_CONTENT
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(appConfig.isTestMode).thenReturn(true)
      when(caseService.deleteAll()).thenReturn(failed(error))

      val result = await(controller.deleteAll()(req))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
      jsonBodyOf(result).toString() shouldEqual """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "create()" should {

    "return 201 when the case has been created successfully" in {
      when(caseService.nextCaseReference).thenReturn(successful("1"))
      when(caseService.insert(any[Case])).thenReturn(successful(c1))

      val result = await(controller.create()(fakeRequest.withBody(toJson(newCase))))

      status(result) shouldEqual CREATED
      jsonBodyOf(result) shouldEqual toJson(c1)
    }

    "return 400 when the JSON request payload is not a Case" in {
      val body = """{"a":"b"}"""
      val result = await(controller.create()(fakeRequest.withBody(toJson(body))))

      status(result) shouldEqual BAD_REQUEST
    }

    "return 500 when an error occurred" in {
      val error = new DatabaseException {
        override def originalDocument: Option[BSONDocument] = None
        override def code: Option[Int] = Some(11000)
        override def message: String = "duplicate value for db index"
      }

      when(caseService.nextCaseReference).thenReturn(successful("1"))
      when(caseService.insert(any[Case])).thenReturn(failed(error))

      val result = await(controller.create()(fakeRequest.withBody(toJson(newCase))))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
      jsonBodyOf(result).toString() shouldEqual """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "update()" should {

    "return 200 when the case has been updated successfully" in {
      when(caseService.update(c1, upsert = false)).thenReturn(successful(Some(c1)))

      val result = await(controller.update(c1.reference)(fakeRequest.withBody(toJson(c1))))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual toJson(c1)
    }

    "return 200 when the case has been updated successfully - with upsert allowed" in {
      when(appConfig.upsertAgents).thenReturn(Seq("agent"))
      when(caseService.update(c1, upsert = true)).thenReturn(successful(Some(c1)))

      val result = await(controller.update(c1.reference)(fakeRequest.withBody(toJson(c1)).withHeaders("User-Agent" -> "agent")))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual toJson(c1)
    }

    "return 400 when the JSON request payload is not a case" in {
      val body = """{"a":"b"}"""
      val result = await(controller.update("")(fakeRequest.withBody(toJson(body))))

      status(result) shouldEqual BAD_REQUEST
    }

    "return 400 when the case reference path parameter does not match the JSON request payload" in {
      val result = await(controller.update("ABC")(fakeRequest.withBody(toJson(c1))))

      status(result) shouldEqual BAD_REQUEST
      jsonBodyOf(result).toString() shouldEqual """{"code":"INVALID_REQUEST_PAYLOAD","message":"Invalid case reference"}"""
    }

    "return 404 when there are no cases with the provided reference" in {
      when(caseService.update(c1, upsert = false)).thenReturn(successful(None))

      val result = await(controller.update(c1.reference)(fakeRequest.withBody(toJson(c1))))

      status(result) shouldEqual NOT_FOUND
      jsonBodyOf(result).toString() shouldEqual """{"code":"NOT_FOUND","message":"Case not found"}"""
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(caseService.update(c1, upsert = false)).thenReturn(failed(error))

      val result = await(controller.update(c1.reference)(fakeRequest.withBody(toJson(c1))))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
      jsonBodyOf(result).toString() shouldEqual """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "get()" should {

    // TODO: test all possible combinations

    val queueId = Some("valid_queueId")
    val assigneeId = Some("valid_assigneeId")
    val sortField = SortField.DAYS_ELAPSED

    "return 200 with the all cases" in {
      val search = Search(Filter(queueId = queueId, assigneeId = assigneeId, status = Some("NEW,OPEN")), Some(Sort(field = sortField)))

      when(caseService.get(refEq(search))).thenReturn(successful(Seq(c1, c2)))

      val result = await(controller.get(search)(fakeRequest))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual toJson(Seq(c1, c2))
    }

    "return 200 with an empty sequence if there are no cases" in {
      val search = Search(Filter(queueId = queueId, assigneeId = assigneeId, status = Some("NEW,OPEN")), Some(Sort(field = sortField)))

      when(caseService.get(search)).thenReturn(successful(Seq.empty))

      val result = await(controller.get(search)(fakeRequest))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual toJson(Seq.empty[Case])
    }

    "return 500 when an error occurred" in {
      val search = Search(Filter(), None)
      val error = new RuntimeException

      when(caseService.get(refEq(search))).thenReturn(failed(error))

      val result = await(controller.get(search)(fakeRequest))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
      jsonBodyOf(result).toString() shouldEqual """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "getByReference()" should {

    "return 200 with the expected case" in {
      when(caseService.getByReference(c1.reference)).thenReturn(successful(Some(c1)))

      val result = await(controller.getByReference(c1.reference)(fakeRequest))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual toJson(c1)
    }

    "return 404 if there are no cases for the specific reference" in {
      when(caseService.getByReference(c1.reference)).thenReturn(successful(None))

      val result = await(controller.getByReference(c1.reference)(fakeRequest))

      status(result) shouldEqual NOT_FOUND
      jsonBodyOf(result).toString() shouldEqual """{"code":"NOT_FOUND","message":"Case not found"}"""
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(caseService.getByReference(c1.reference)).thenReturn(failed(error))

      val result = await(controller.getByReference(c1.reference)(fakeRequest))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
      jsonBodyOf(result).toString() shouldEqual """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

}
