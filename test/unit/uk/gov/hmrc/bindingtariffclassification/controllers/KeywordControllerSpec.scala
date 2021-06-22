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
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.Json.toJson
import play.api.test.FakeRequest
import play.api.test.Helpers._
import reactivemongo.bson.BSONDocument
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters._
import uk.gov.hmrc.bindingtariffclassification.model.Role.CLASSIFICATION_OFFICER
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.KeywordService
import uk.gov.hmrc.http.HttpVerbs
import util.CaseData

import scala.concurrent.Future._

class KeywordControllerSpec extends BaseSpec with BeforeAndAfterEach {

  override protected def beforeEach(): Unit =
    Mockito.reset(keywordService)

  private val newKeywordRequest: NewKeywordRequest = CaseData.createNewKeyword()
  private val keyword1: Keyword                    = CaseData.createKeyword()
  private val keyword2: Keyword                    = CaseData.createKeyword()
  private val pagination                           = Pagination()

  private val caseHeader = CaseHeader(
    reference = "9999999999",
    Some(Operator("0", None, None, CLASSIFICATION_OFFICER, List(), List())),
    Some("3"),
    Some("Smartphone"),
    ApplicationType.BTI,
    CaseStatus.OPEN,
    0,
    None
  )

  private val caseKeyword  = CaseKeyword(Keyword("tool"), List(caseHeader))
  private val caseKeyword2 = CaseKeyword(Keyword("bike"), List(caseHeader))

  private val keywordService = mock[KeywordService]
  private val appConfig      = mock[AppConfig]

  private val fakeRequest = FakeRequest()

  private val controller = new KeywordController(appConfig, keywordService, mcc)

  "deleteKeyword" should {

    val req = FakeRequest(method = HttpVerbs.DELETE, path = "/keyword/name")

    "return 204 idf the test mode is enabled" in {
      when(keywordService.deleteKeyword(refEq("name"))).thenReturn(successful(()))

      val result = controller.deleteKeyword("name")(req).futureValue

      result.header.status shouldBe NO_CONTENT
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(keywordService.deleteKeyword(refEq("name"))).thenReturn(failed(error))

      val result = controller.deleteKeyword("name")(req)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "addKeyword" should {

    "return 201 when the keyword has been created successfully" in {
      when(keywordService.addKeyword(any[Keyword])).thenReturn(successful(keyword1))

      val result = controller.addKeyword()(fakeRequest.withBody(toJson(newKeywordRequest)))

      status(result) shouldBe CREATED
      contentAsJson(result) shouldBe toJson(keyword1)
    }

    "return 400 when the JSON request payload is not a Keyword" in {
      val body   = """{"a":"b"}"""
      val result = controller.addKeyword()(fakeRequest.withBody(toJson(body))).futureValue

      result.header.status shouldBe BAD_REQUEST
    }

    "return 500 when an error occurred" in {
      val errorCode: Int = 11000
      val error = new DatabaseException {
        override def originalDocument: Option[BSONDocument] = None

        override def code: Option[Int] = Some(errorCode)

        override def message: String = "duplicate value for db index"
      }

      when(keywordService.addKeyword(any[Keyword])).thenReturn(failed(error))

      val result = controller.addKeyword()(fakeRequest.withBody(toJson(newKeywordRequest)))

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "approveKeyword" should {

    "return 200 when the keyword has been updated/approved successfully" in {
      when(keywordService.approveKeyword(keyword1, upsert = false)).thenReturn(successful(Some(keyword1)))

      val result = controller.approveKeyword(keyword1.name)(fakeRequest.withBody(toJson(keyword1)))

      status(result) shouldBe OK
      contentAsJson(result) shouldBe toJson(keyword1)
    }

    "return 400 when the JSON request payload is invalid" in {
      val body   = """{"a":"b"}"""
      val result = controller.approveKeyword("")(fakeRequest.withBody(toJson(body))).futureValue

      result.header.status shouldBe BAD_REQUEST
    }

    "return 404 when there are no keywords with the provided name" in {
      val keyword3 = Keyword("not in the list")
      when(keywordService.approveKeyword(keyword3, upsert = false)).thenReturn(successful(None))

      val result = controller.approveKeyword(keyword3.name)(fakeRequest.withBody(toJson(keyword3)))

      status(result) shouldBe NOT_FOUND
      contentAsJson(result).toString() shouldBe """{"code":"NOT_FOUND","message":"Keyword not found"}"""
    }

    "return 500 when an error occurred" in {
      val error    = new RuntimeException
      val keyword3 = Keyword("not in the list")

      when(keywordService.approveKeyword(keyword3, upsert = false)).thenReturn(failed(error))

      val result = controller.approveKeyword(keyword3.name)(fakeRequest.withBody(toJson(keyword3)))

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }
  }

  "getAllKeywords" should {
    "return 200 with the all keywords from the collection" in {
      when(keywordService.findAll(refEq(pagination)))
        .thenReturn(successful(Paged(Seq(keyword1, keyword2))))

      val result = controller.getAllKeywords(pagination)(fakeRequest)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe toJson(Paged(Seq(keyword1, keyword2)))
    }

    "return 200 with an empty sequence if there are no keywords" in {
      when(keywordService.findAll(refEq(pagination)))
        .thenReturn(successful(Paged.empty[Keyword]))

      val result = controller.getAllKeywords(pagination)(fakeRequest)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe toJson(Paged.empty[Keyword])
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(keywordService.findAll(refEq(pagination)))
        .thenReturn(failed(error))

      val result = controller.getAllKeywords(pagination)(fakeRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result)
        .toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }
  }

  "fetchCaseKeywords" should {
    "return 200 with the all cases that contain keywords from the collection" in {
      when(keywordService.fetchCaseKeywords(refEq(pagination)))
        .thenReturn(successful(Paged(Seq(caseKeyword, caseKeyword2))))

      val result = controller.fetchCaseKeywords(pagination)(fakeRequest)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe toJson(Paged(Seq(caseKeyword, caseKeyword2)))
    }

    "return 200 with an empty sequence if there are no cases containing keywords" in {
      when(keywordService.fetchCaseKeywords(refEq(pagination)))
        .thenReturn(successful(Paged.empty[CaseKeyword]))

      val result = controller.fetchCaseKeywords(pagination)(fakeRequest)

      status(result) shouldBe OK
      contentAsJson(result) shouldBe toJson(Paged.empty[CaseKeyword])
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(keywordService.fetchCaseKeywords(refEq(pagination)))
        .thenReturn(failed(error))

      val result = controller.fetchCaseKeywords(pagination)(fakeRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result)
        .toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }
  }
}
