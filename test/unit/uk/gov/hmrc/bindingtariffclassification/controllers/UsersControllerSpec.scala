/*
 * Copyright 2023 HM Revenue & Customs
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
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.UsersService
import util.{CaseData, DatabaseException}

import scala.concurrent.Future._

class UsersControllerSpec extends BaseSpec with BeforeAndAfterEach {

  override protected def beforeEach(): Unit =
    Mockito.reset(usersService)

  private val newUser: NewUserRequest = CaseData.createNewUser()
  private val user1: Operator         = CaseData.createUser()
  private val user2: Operator         = CaseData.createUser()

  private val appConfig    = mock[AppConfig]
  private val usersService = mock[UsersService]

  private val fakeRequest = FakeRequest()

  private val controller = new UsersController(appConfig, usersService, mcc)

  "fetchUserDetails" should {

    "return 200 with the expected user" in {
      when(usersService.getUserById(user1.id))
        .thenReturn(successful(Some(user1)))

      val result = controller.fetchUserDetails(user1.id)(fakeRequest)

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe toJson(user1)
    }

    "return 404 if there are no users for the specific reference" in {
      when(usersService.getUserById(user1.id)).thenReturn(successful(None))

      val result = controller.fetchUserDetails(user1.id)(fakeRequest)

      status(result) shouldBe NOT_FOUND
      contentAsJson(result)
        .toString() shouldBe """{"code":"NOT_FOUND","message":"User not found"}"""
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(usersService.getUserById(user1.id)).thenReturn(failed(error))

      val result = controller.fetchUserDetails(user1.id)(fakeRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result)
        .toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }
  }

  "createUser" should {

    "return 201 when the user has been created successfully" in {
      when(usersService.insertUser(any[Operator])).thenReturn(successful(user1))

      val result = controller.createUser()(fakeRequest.withBody(toJson(newUser)))

      status(result)        shouldBe CREATED
      contentAsJson(result) shouldBe toJson(user1)
    }

    "return 400 when the JSON request payload is not a User" in {
      val body   = """{"a":"b"}"""
      val result = controller.createUser()(fakeRequest.withBody(toJson(body)))

      status(result) shouldBe BAD_REQUEST
    }

    "return 500 when an error occurred" in {
      val errorCode: Int = 11000
      val error          = DatabaseException.exception(errorCode, "duplicate value for db index")

      when(usersService.insertUser(any[Operator])).thenReturn(failed(error))

      val result = controller.createUser()(fakeRequest.withBody(toJson(newUser)))

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result)
        .toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "updateUser" should {

    "return 200 when the user has been updated successfully" in {
      when(usersService.updateUser(user1, upsert = false))
        .thenReturn(successful(Some(user1)))

      val result = controller.updateUser(user1.id)(fakeRequest.withBody(toJson(user1)))

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe toJson(user1)
    }

    "return 200 when the user has been updated successfully - with upsert allowed" in {
      when(appConfig.upsertAgents).thenReturn(Seq("agent"))
      when(usersService.updateUser(user1, upsert = true))
        .thenReturn(successful(Some(user1)))

      val result =
        controller.updateUser(user1.id)(
          fakeRequest
            .withBody(toJson(user1))
            .withHeaders("User-Agent" -> "agent")
        )

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe toJson(user1)
    }

    "return 400 when the JSON request payload is not a user" in {
      val body   = """{"a":"b"}"""
      val result = controller.updateUser("")(fakeRequest.withBody(toJson(body))).futureValue

      result.header.status shouldBe BAD_REQUEST
    }

    "return 400 when the user id path parameter does not match the JSON request payload" in {
      val result = controller.updateUser("ABC")(fakeRequest.withBody(toJson(user1)))

      status(result) shouldBe BAD_REQUEST
      contentAsJson(result)
        .toString() shouldBe """{"code":"INVALID_REQUEST_PAYLOAD","message":"Invalid user id"}"""
    }

    "return 404 when there are no users with the provided reference" in {
      when(usersService.updateUser(user1, upsert = false))
        .thenReturn(successful(None))

      val result = controller.updateUser(user1.id)(fakeRequest.withBody(toJson(user1)))

      status(result) shouldBe NOT_FOUND
      contentAsJson(result)
        .toString() shouldBe """{"code":"NOT_FOUND","message":"User not found"}"""
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(usersService.updateUser(user1, upsert = false))
        .thenReturn(failed(error))

      val result = controller.updateUser(user1.id)(fakeRequest.withBody(toJson(user1)))

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result)
        .toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "markDeleted" should {
    val userDeleted = user1.copy(deleted = true)

    "return 200 when the user has been marked deleted successfully" in {

      when(usersService.updateUser(userDeleted, upsert = false))
        .thenReturn(successful(Some(userDeleted)))

      val result = controller.markDeleted(user1.id)(fakeRequest.withBody(toJson(user1)))

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe toJson(userDeleted)
    }

    "return 200 when the user has been marked deleted successfully - with upsert allowed" in {

      when(appConfig.upsertAgents).thenReturn(Seq("agent"))
      when(usersService.updateUser(userDeleted, upsert = true))
        .thenReturn(successful(Some(userDeleted)))

      val result =
        controller.markDeleted(user1.id)(
          fakeRequest
            .withBody(toJson(user1))
            .withHeaders("User-Agent" -> "agent")
        )

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe toJson(userDeleted)
    }

    "return 400 when the JSON request payload is not a user" in {
      val body   = """{"a":"b"}"""
      val result = controller.markDeleted("")(fakeRequest.withBody(toJson(body))).futureValue

      result.header.status shouldBe BAD_REQUEST
    }

    "return 400 when the user id path parameter does not match the JSON request payload" in {
      val result = controller.markDeleted("ABC")(fakeRequest.withBody(toJson(user1)))

      status(result) shouldBe BAD_REQUEST
      contentAsJson(result)
        .toString() shouldBe """{"code":"INVALID_REQUEST_PAYLOAD","message":"Invalid user id"}"""
    }

    "return 404 when there are no users with the provided reference" in {
      when(usersService.updateUser(userDeleted, upsert = false))
        .thenReturn(successful(None))

      val result = controller.markDeleted(user1.id)(fakeRequest.withBody(toJson(user1)))

      status(result) shouldBe NOT_FOUND
      contentAsJson(result)
        .toString() shouldBe """{"code":"NOT_FOUND","message":"User not found"}"""
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(usersService.updateUser(userDeleted, upsert = false))
        .thenReturn(failed(error))

      val result = controller.markDeleted(user1.id)(fakeRequest.withBody(toJson(user1)))

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result)
        .toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }
  }

  "allUsers" should {

    val role = Some(Set(Role.CLASSIFICATION_OFFICER))
    val team = Some("ACT")

    val search     = UserSearch(role = role, team = team)
    val pagination = Pagination()

    "return 200 with the expected users" in {
      when(usersService.search(refEq(search), refEq(pagination)))
        .thenReturn(successful(Paged(Seq(user1, user2))))

      val result = controller.allUsers(search, pagination)(fakeRequest)

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe toJson(Paged(Seq(user1, user2)))
    }

    "return 200 with an empty sequence if there are no users" in {
      when(usersService.search(search, pagination))
        .thenReturn(successful(Paged.empty[Operator]))

      val result = controller.allUsers(search, pagination)(fakeRequest)

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe toJson(Paged.empty[Operator])
    }

    "return 500 when an error occurred" in {

      val search = UserSearch(None, None)
      val error  = new RuntimeException

      when(usersService.search(refEq(search), refEq(pagination)))
        .thenReturn(failed(error))

      val result = controller.allUsers(search, pagination)(fakeRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result)
        .toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }
  }
}
