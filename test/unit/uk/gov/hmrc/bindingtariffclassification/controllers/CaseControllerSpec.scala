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

import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.Mockito.{verify, when}
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.Json.toJson
import play.api.test.FakeRequest
import play.api.test.Helpers._
import reactivemongo.bson.BSONDocument
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.bindingtariffclassification.sort.{CaseSortField, SortDirection}
import uk.gov.hmrc.http.HttpVerbs
import util.CaseData

import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.Future._

// scalastyle:off magic.number
class CaseControllerSpec extends BaseSpec with BeforeAndAfterEach {

  override protected def beforeEach(): Unit =
    Mockito.reset(caseService)

  private val newCase: NewCaseRequest = CaseData.createNewCase()
  private val c1: Case                = CaseData.createCase()
  private val c2: Case                = CaseData.createCase()

  private val caseService = mock[CaseService]
  private val appConfig   = mock[AppConfig]

  private val fakeRequest = FakeRequest()

  private val controller = new CaseController(appConfig, caseService, parser, mcc)

  "deleteAll()" should {

    val req = FakeRequest(method = HttpVerbs.DELETE, path = "/cases")

    "return 403 if the test mode is disabled" in {
      when(appConfig.isTestMode).thenReturn(false)
      val result = controller.deleteAll()(req)

      status(result) shouldBe FORBIDDEN
      contentAsJson(result)
        .toString() shouldBe s"""{"code":"FORBIDDEN","message":"You are not allowed to call ${req.method} ${req.path}"}"""
    }

    "return 204 if the test mode is enabled" in {
      when(appConfig.isTestMode).thenReturn(true)
      when(caseService.deleteAll()).thenReturn(successful(()))

      val result = controller.deleteAll()(req).futureValue

      result.header.status shouldBe NO_CONTENT
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(appConfig.isTestMode).thenReturn(true)
      when(caseService.deleteAll()).thenReturn(failed(error))

      val result = controller.deleteAll()(req)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "delete()" should {

    val req = FakeRequest(method = HttpVerbs.DELETE, path = "/cases/ref")

    "return 403 if the test mode is disabled" in {
      when(appConfig.isTestMode).thenReturn(false)
      val result = controller.delete("ref")(req)

      status(result) shouldBe FORBIDDEN
      contentAsJson(result)
        .toString() shouldBe s"""{"code":"FORBIDDEN","message":"You are not allowed to call ${req.method} ${req.path}"}"""
    }

    "return 204 if the test mode is enabled" in {
      when(appConfig.isTestMode).thenReturn(true)
      when(caseService.delete(refEq("ref"))).thenReturn(successful(()))

      val result = controller.delete("ref")(req).futureValue

      result.header.status shouldBe NO_CONTENT
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(appConfig.isTestMode).thenReturn(true)
      when(caseService.delete(refEq("ref"))).thenReturn(failed(error))

      val result = controller.delete("ref")(req)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "create()" should {

    "return 201 when the case has been created successfully" in {
      when(caseService.nextCaseReference(ApplicationType.BTI)).thenReturn(successful("1"))
      when(caseService.insert(any[Case])).thenReturn(successful(c1))
      when(caseService.addInitialSampleStatusIfExists(any[Case])).thenReturn(Future.successful((): Unit))

      val result = controller.create()(fakeRequest.withBody(toJson(newCase)))

      status(result) shouldBe CREATED
      contentAsJson(result) shouldBe toJson(c1)
    }

    "return 400 when the JSON request payload is not a Case" in {
      val body   = """{"a":"b"}"""
      val result = controller.create()(fakeRequest.withBody(toJson(body))).futureValue

      result.header.status shouldBe BAD_REQUEST
    }

    "return 500 when an error occurred" in {
      val error = new DatabaseException {
        override def originalDocument: Option[BSONDocument] = None
        override def code: Option[Int]                      = Some(11000)
        override def message: String                        = "duplicate value for db index"
      }

      when(caseService.nextCaseReference(ApplicationType.BTI)).thenReturn(successful("1"))
      when(caseService.insert(any[Case])).thenReturn(failed(error))

      val result = controller.create()(fakeRequest.withBody(toJson(newCase)))

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "put()" should {

    "return 200 when the case has been updated successfully" in {
      when(caseService.update(c1, upsert = false)).thenReturn(successful(Some(c1)))

      val result = controller.put(c1.reference)(fakeRequest.withBody(toJson(c1)))

      status(result) shouldBe OK
      contentAsJson(result) shouldBe toJson(c1)
    }

    "return 200 when the case has been updated successfully - with upsert allowed" in {
      when(appConfig.upsertAgents).thenReturn(Seq("agent"))
      when(caseService.update(c1, upsert = true)).thenReturn(successful(Some(c1)))

      val result =
        controller.put(c1.reference)(fakeRequest.withBody(toJson(c1)).withHeaders("User-Agent" -> "agent"))

      status(result) shouldBe OK
      contentAsJson(result) shouldBe toJson(c1)
    }

    "return 200 when the case has been updated successfully with new fields from migration" in {

      val caseWithNewFields = c1.copy(migratedDaysElapsed = Some(5))
      when(appConfig.upsertAgents).thenReturn(Seq("agent"))
      when(caseService.update(caseWithNewFields, upsert = true)).thenReturn(successful(Some(c1)))

      val newCaseJson = toJson(caseWithNewFields)
      val captor      = ArgumentCaptor.forClass(classOf[Case])

      val result =
        controller.put(c1.reference)(fakeRequest.withBody(newCaseJson).withHeaders("User-Agent" -> "agent")).futureValue

      verify(caseService).update(captor.capture(), upsert = any[Boolean])
      val createdCase: Case = captor.getValue

      createdCase.migratedDaysElapsed shouldBe Some(5)
      result.header.status shouldBe OK
    }

    "return 400 when the JSON request payload is not a case" in {
      val body   = """{"a":"b"}"""
      val result = controller.put("")(fakeRequest.withBody(toJson(body))).futureValue

      result.header.status shouldBe BAD_REQUEST
    }

    "return 400 when the case reference path parameter does not match the JSON request payload" in {
      val result = controller.put("ABC")(fakeRequest.withBody(toJson(c1)))

      status(result) shouldBe BAD_REQUEST
      contentAsJson(result)
        .toString() shouldBe """{"code":"INVALID_REQUEST_PAYLOAD","message":"Invalid case reference"}"""
    }

    "return 404 when there are no cases with the provided reference" in {
      when(caseService.update(c1, upsert = false)).thenReturn(successful(None))

      val result = controller.put(c1.reference)(fakeRequest.withBody(toJson(c1)))

      status(result) shouldBe NOT_FOUND
      contentAsJson(result).toString() shouldBe """{"code":"NOT_FOUND","message":"Case not found"}"""
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(caseService.update(c1, upsert = false)).thenReturn(failed(error))

      val result = controller.put(c1.reference)(fakeRequest.withBody(toJson(c1)))

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "update()" should {

    "return 200 when the case has been updated successfully" in {
      val applicationPdf = Some(Attachment("id", public = true, None, Instant.now, None))
      val caseUpdate = CaseUpdate(application = Some(BTIUpdate(applicationPdf = SetValue(applicationPdf))))
      when(caseService.update(c1.reference, caseUpdate)).thenReturn(successful(Some(c1)))

      val result = controller.update(c1.reference)(fakeRequest.withBody(toJson(caseUpdate)))

      status(result) shouldBe OK
      contentAsJson(result) shouldBe toJson(c1)
    }

    "return 400 when the JSON request payload is not a case" in {
      val body   = """{"a":"b"}"""
      val result = controller.update("")(fakeRequest.withBody(toJson(body))).futureValue

      result.header.status shouldBe BAD_REQUEST
    }

    "return 404 when there are no cases with the provided reference" in {
      val caseUpdate = CaseUpdate()
      when(caseService.update(c1.reference, caseUpdate)).thenReturn(successful(None))

      val result = controller.update(c1.reference)(fakeRequest.withBody(toJson(caseUpdate)))

      status(result) shouldBe NOT_FOUND
      contentAsJson(result).toString() shouldBe """{"code":"NOT_FOUND","message":"Case not found"}"""
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException
      val caseUpdate = CaseUpdate()
      when(caseService.update(c1.reference, caseUpdate)).thenReturn(failed(error))

      val result = controller.update(c1.reference)(fakeRequest.withBody(toJson(caseUpdate)))

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "get()" should {

    // TODO: test all possible combinations

    val queueId    = Some(Set("valid_queueId"))
    val assigneeId = Some("valid_assigneeId")

    val search = CaseSearch(
      filter = CaseFilter(
        queueId    = queueId,
        assigneeId = assigneeId,
        statuses   = Some(Set(PseudoCaseStatus.NEW, PseudoCaseStatus.OPEN))
      ),
      sort = Some(CaseSort(field = Set(CaseSortField.DAYS_ELAPSED), direction = SortDirection.DESCENDING))
    )

    val pagination = Pagination()

    "return 200 with the expected cases" in {
      when(caseService.get(refEq(search), refEq(pagination))).thenReturn(successful(Paged(Seq(c1, c2))))

      val result = controller.get(search, pagination)(fakeRequest)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe toJson(Paged(Seq(c1, c2)))
    }

    "return 200 with an empty sequence if there are no cases" in {
      when(caseService.get(search, pagination)).thenReturn(successful(Paged.empty[Case]))

      val result = controller.get(search, pagination)(fakeRequest)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe toJson(Paged.empty[Case])
    }

    "return 500 when an error occurred" in {
      val search = CaseSearch(CaseFilter(), None)
      val error  = new RuntimeException

      when(caseService.get(refEq(search), refEq(pagination))).thenReturn(failed(error))

      val result = controller.get(search, pagination)(fakeRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "getByReference()" should {

    "return 200 with the expected case" in {
      when(caseService.getByReference(c1.reference)).thenReturn(successful(Some(c1)))

      val result = controller.getByReference(c1.reference)(fakeRequest)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe toJson(c1)
    }

    "return 404 if there are no cases for the specific reference" in {
      when(caseService.getByReference(c1.reference)).thenReturn(successful(None))

      val result = controller.getByReference(c1.reference)(fakeRequest)

      status(result) shouldBe NOT_FOUND
      contentAsJson(result).toString() shouldBe """{"code":"NOT_FOUND","message":"Case not found"}"""
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(caseService.getByReference(c1.reference)).thenReturn(failed(error))

      val result = controller.getByReference(c1.reference)(fakeRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

}
