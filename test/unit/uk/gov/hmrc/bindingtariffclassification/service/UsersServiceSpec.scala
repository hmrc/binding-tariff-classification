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

package uk.gov.hmrc.bindingtariffclassification.service

import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.test.Helpers._
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.repository._

import scala.concurrent.Future.successful

class UsersServiceSpec extends BaseSpec with BeforeAndAfterEach {

  private val user      = mock[Operator]
  private val userSaved = mock[Operator]

  private val appConfig       = mock[AppConfig]
  private val usersRepository = mock[UsersRepository]

  private val service =
    new UsersService(usersRepository)

  private final val emulatedFailure = new RuntimeException("Emulated failure.")

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(usersRepository, appConfig)
  }

  override protected def beforeEach(): Unit =
    super.beforeEach()

  "getUserById" should {

    "return the expected user" in {
      when(usersRepository.getById(user.id)).thenReturn(successful(Some(user)))

      val result = await(service.getUserById(user.id))
      result shouldBe Some(user)
    }

    "return None when the user is not found" in {
      when(usersRepository.getById(user.id)).thenReturn(successful(None))

      val result = await(service.getUserById(user.id))
      result shouldBe None
    }

    "propagate any error" in {
      when(usersRepository.getById(user.id)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.getUserById(user.id))
      }
      caught shouldBe emulatedFailure
    }
  }

  "insertUser" should {

    "return the user after it is inserted in the database collection" in {
      when(usersRepository.insert(user)).thenReturn(successful(userSaved))

      await(service.insertUser(user)) shouldBe userSaved
    }

    "propagate any error" in {
      when(usersRepository.insert(user)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.insertUser(user))
      }
      caught shouldBe emulatedFailure
    }
  }

  "updateUser" should {

    "return the user after it is updated in the database collection" in {
      when(usersRepository.update(user, upsert = false))
        .thenReturn(successful(Some(userSaved)))

      await(service.updateUser(user, upsert = false)) shouldBe Some(userSaved)
    }

    "return None if the user does not exist in the database collection" in {
      when(usersRepository.update(user, upsert = false))
        .thenReturn(successful(None))

      val result = await(service.updateUser(user, upsert = false))
      result shouldBe None
    }

    "propagate any error" in {
      when(usersRepository.update(user, upsert = false))
        .thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.updateUser(user, upsert = false))
      }
      caught shouldBe emulatedFailure
    }
  }

  "search" should {

    val searchBy   = mock[UserSearch]
    val pagination = mock[Pagination]

    "return the expected users" in {
      when(usersRepository.search(searchBy, pagination))
        .thenReturn(successful(Paged(Seq(user))))

      await(service.search(searchBy, pagination)) shouldBe Paged(Seq(user))
    }

    "propagate any error" in {
      when(usersRepository.search(searchBy, pagination))
        .thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.search(searchBy, pagination))
      }
      caught shouldBe emulatedFailure
    }
  }
}
