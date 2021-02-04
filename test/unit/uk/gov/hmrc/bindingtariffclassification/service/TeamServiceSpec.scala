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

package uk.gov.hmrc.bindingtariffclassification.service

import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.Team
import uk.gov.hmrc.bindingtariffclassification.repository.TeamRepository

import scala.concurrent.Future.successful


class TeamServiceSpec extends BaseSpec with BeforeAndAfterEach {

  private val team1      = mock[Team]
  private val team1Saved = mock[Team]

  private val teamRepository            = mock[TeamRepository]
  private val appConfig                 = mock[AppConfig]

  private val service =
    new TeamService(
      appConfig,
      teamRepository
    )

  private final val emulatedFailure = new RuntimeException("Emulated failure.")

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(teamRepository,appConfig)
  }

  override protected def beforeEach(): Unit =
    super.beforeEach()

  "insert()" should {

    "return the team after it is inserted in the database collection" in {
      when(teamRepository.insert(team1)).thenReturn(successful(team1Saved))

      await(service.insert(team1)) shouldBe team1Saved
    }

    "propagate any error" in {
      when(teamRepository.insert(team1)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.insert(team1))
      }
      caught shouldBe emulatedFailure
    }
  }

  "update()" should {

    "return the team after it is updated in the database collection" in {
      when(teamRepository.update(team1, upsert = false)).thenReturn(successful(Some(team1Saved)))

      await(service.update(team1, upsert = false)) shouldBe Some(team1Saved)
    }

    "return None if the team does not exist in the database collection" in {
      when(teamRepository.update(team1, upsert = false)).thenReturn(successful(None))

      val result = await(service.update(team1, upsert = false))
      result shouldBe None
    }

    "propagate any error" in {
      when(teamRepository.update(team1, upsert = false)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.update(team1, upsert = false))
      }
      caught shouldBe emulatedFailure
    }

  }

  "getById()" should {

    "return the expected team" in {
      when(teamRepository.getById(team1.id)).thenReturn(successful(Some(team1)))

      val result = await(service.getById(team1.id))
      result shouldBe Some(team1)
    }

    "return None when the team is not found" in {
      when(teamRepository.getById(team1.id)).thenReturn(successful(None))

      val result = await(service.getById(team1.id))
      result shouldBe None
    }

    "propagate any error" in {
      when(teamRepository.getById(team1.id)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.getById(team1.id))
      }
      caught shouldBe emulatedFailure
    }

  }

}
