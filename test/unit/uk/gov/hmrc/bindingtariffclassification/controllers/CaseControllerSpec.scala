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

  private val c: Case = CaseData.createCase()
  private val mockCaseService = mock[CaseService]

  private val fakeRequest = FakeRequest("POST", "/cases")

  private val controller = new CaseController(mockCaseService)

  "create()" should {

    "return 201 when the case has been created successfully" in {
      when(mockCaseService.insert(c)).thenReturn(successful(c))

      val result = await(controller.create()(fakeRequest.withBody(toJson(c))))

      status(result) shouldEqual CREATED
      jsonBodyOf(result) shouldEqual toJson(c)
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

      when(mockCaseService.insert(c)).thenReturn(failed(error))

      val result = await(controller.create()(fakeRequest.withBody(toJson(c))))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
    }

  }

  "update()" should {

    "return 200 when the case has been updated successfully" in {
      // TODO
    }

    "return 400 when the JSON request payload is not a case" in {
      // TODO
    }

    "return 404 when there are no cases with the provided reference" in {
      // TODO
    }

    "return 500 when an error occurred" in {
      // TODO
    }

  }

  "getAll()" should {
    // TODO
  }

  "getByReference()" should {
    // TODO
  }

}
