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
import play.api.http.Status.{BAD_REQUEST, CREATED, INTERNAL_SERVER_ERROR, NOT_FOUND, OK}
import play.api.libs.json.Json.toJson
import play.api.test.FakeRequest
import reactivemongo.bson.BSONDocument
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.bindingtariffclassification.controllers.CaseController
import uk.gov.hmrc.bindingtariffclassification.model.Case
import uk.gov.hmrc.bindingtariffclassification.model.JsonFormatters._
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.bindingtariffclassification.todelete.CaseData
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future._

class CaseControllerSpec extends UnitSpec with WithFakeApplication with MockitoSugar {

  private implicit val mat: Materializer = fakeApplication.materializer

  private val c1: Case = CaseData.createCase()
  private val c2: Case = CaseData.createCase()

  private val mockCaseService = mock[CaseService]

  private val fakeRequest = FakeRequest()

  private val controller = new CaseController(mockCaseService)

  "create()" should {

    "return 201 when the case has been created successfully" in {
      when(mockCaseService.insert(c1)).thenReturn(successful(c1))

      val result = await(controller.create()(fakeRequest.withBody(toJson(c1))))

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

      when(mockCaseService.insert(c1)).thenReturn(failed(error))

      val result = await(controller.create()(fakeRequest.withBody(toJson(c1))))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
    }

  }

  "update()" should {

    "return 200 when the case has been updated successfully" in {
      when(mockCaseService.update(c1)).thenReturn(successful(Some(c1)))

      val result = await(controller.update(c1.reference)(fakeRequest.withBody(toJson(c1))))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual toJson(c1)
    }

    "return 400 when the JSON request payload is not a case" in {
      val body = """{"a":"b"}"""
      val result = await(controller.update("")(fakeRequest.withBody(toJson(body))))

      status(result) shouldEqual BAD_REQUEST
    }

    "return 404 when there are no cases with the provided reference" in {
      when(mockCaseService.update(c1)).thenReturn(successful(None))

      val result = await(controller.update(c1.reference)(fakeRequest.withBody(toJson(c1))))

      status(result) shouldEqual NOT_FOUND
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(mockCaseService.update(c1)).thenReturn(failed(error))

      val result = await(controller.update("")(fakeRequest.withBody(toJson(c1))))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
    }

  }

  "getAll()" should {

    "return 200 with the all cases" in {
      when(mockCaseService.getAll).thenReturn(successful(Seq(c1, c2)))

      val result = await(controller.getAll(fakeRequest))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual toJson(Seq(c1, c2))
    }

    "return 200 with an empty sequence if there are no cases" in {
      when(mockCaseService.getAll).thenReturn(successful(Nil))

      val result = await(controller.getAll(fakeRequest))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual toJson(Seq[Case]())
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(mockCaseService.getAll).thenReturn(failed(error))

      val result = await(controller.getAll(fakeRequest))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
    }

  }

  "getByReference()" should {

    "return 200 with the expected case" in {
      when(mockCaseService.getByReference(c1.reference)).thenReturn(successful(Some(c1)))

      val result = await(controller.getByReference(c1.reference)(fakeRequest))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual toJson(c1)
    }

    "return 404 if there are no cases for the specific reference" in {
      when(mockCaseService.getByReference(c1.reference)).thenReturn(successful(None))

      val result = await(controller.getByReference(c1.reference)(fakeRequest))

      status(result) shouldEqual NOT_FOUND
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(mockCaseService.getByReference(c1.reference)).thenReturn(failed(error))

      val result = await(controller.getByReference(c1.reference)(fakeRequest))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
    }

  }

}
