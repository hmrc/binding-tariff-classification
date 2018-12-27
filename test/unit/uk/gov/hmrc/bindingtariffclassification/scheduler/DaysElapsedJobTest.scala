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
import java.util.concurrent.TimeUnit.{DAYS, SECONDS}

import org.mockito.BDDMockito.given
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.config.{AppConfig, DaysElapsedConfig}
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class DaysElapsedJobTest extends UnitSpec with MockitoSugar {

  private val zone = ZoneId.of("UTC")
  private val elevenPM = LocalTime.of(23, 0).atDate(LocalDate.now()).atZone(zone).toInstant
  private val clock = Clock.fixed(elevenPM, zone)
  private val caseService = mock[CaseService]
  private val appConfig = mock[AppConfig]
  private val lockRepository = mock[LockRepository]
  private val job = new DaysElapsedJob(appConfig, caseService, lockRepository)

  "Scheduled Job" should {
    given(appConfig.clock).willReturn(clock)

    "Configure 'Name'" in {
      job.name shouldBe "DaysElapsed"
    }

    "Configure 'initialDelay' today" in {
      given(appConfig.daysElapsed).willReturn(DaysElapsedConfig(LocalTime.MIDNIGHT, 1))

      job.initialDelay shouldBe FiniteDuration(60*60, SECONDS)
    }

    "Configure 'initialDelay' tomorrow" in {
      given(appConfig.daysElapsed).willReturn(DaysElapsedConfig(LocalTime.of(22,0), 1))

      job.initialDelay shouldBe FiniteDuration(23*60*60, SECONDS)
    }

    "Configure 'interval'" in {
      given(appConfig.daysElapsed).willReturn(DaysElapsedConfig(LocalTime.MIDNIGHT, 1))

      job.interval shouldBe FiniteDuration(1, DAYS)
    }

    "Execute in lock" in {
      given(appConfig.daysElapsed).willReturn(DaysElapsedConfig(LocalTime.MIDNIGHT, 1))
      given(caseService.incrementDaysElapsedIfAppropriate(1, clock)).willReturn(Future.successful(2))

      await(job.executeInLock).message shouldBe "Incremented the Days Elapsed for [2] cases."
    }

  }

}
