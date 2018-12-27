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

package uk.gov.hmrc.bindingtariffclassification.scheduler

import java.time._
import java.util.concurrent.TimeUnit.DAYS

import org.mockito.BDDMockito.given
import org.mockito.Mockito.reset
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.config.{AppConfig, DaysElapsedConfig}
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class DaysElapsedJobTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val zone = ZoneId.of("UTC")
  private val lunchtime = instantOf("2018-12-25T12:00:00")
  private val clock = Clock.fixed(lunchtime, zone)
  private val caseService = mock[CaseService]
  private val appConfig = mock[AppConfig]

  private def instantOf(datetime: String): Instant = {
    LocalDateTime.parse(datetime).atZone(zone).toInstant
  }

  override def beforeEach(): Unit = {
    given(appConfig.clock).willReturn(clock)
  }

  override def afterEach(): Unit = {
    reset(appConfig, caseService)
  }

  "Scheduled Job" should {

    "Configure 'Name'" in {
      new DaysElapsedJob(appConfig, caseService).name shouldBe "DaysElapsed"
    }

    "Configure 'initialDelay' with first run today" in {
      given(appConfig.daysElapsed).willReturn(DaysElapsedConfig(LocalTime.of(14,0), 1))

      new DaysElapsedJob(appConfig, caseService).firstRunDate shouldBe instantOf("2018-12-25T14:00:00")
    }

    "Configure 'initialDelay' with first run tomorrow" in {
      given(appConfig.daysElapsed).willReturn(DaysElapsedConfig(LocalTime.of(10,0), 1))

      new DaysElapsedJob(appConfig, caseService).firstRunDate shouldBe instantOf("2018-12-26T10:00:00")
    }

    "Configure 'interval'" in {
      given(appConfig.daysElapsed).willReturn(DaysElapsedConfig(LocalTime.MIDNIGHT, 1))

      new DaysElapsedJob(appConfig, caseService).interval shouldBe FiniteDuration(1, DAYS)
    }

    "Execute" in {
      given(appConfig.daysElapsed).willReturn(DaysElapsedConfig(LocalTime.MIDNIGHT, 1))
      given(caseService.incrementDaysElapsedIfAppropriate(1, clock)).willReturn(Future.successful(2))

      await(new DaysElapsedJob(appConfig, caseService).execute()) shouldBe "Incremented the Days Elapsed for [2] cases."
    }

  }

}
