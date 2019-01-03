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

package uk.gov.hmrc.bindingtariffclassification.scheduler

import java.time._
import java.util.concurrent.TimeUnit.DAYS

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito.given
import org.mockito.Mockito.{reset, verify, verifyZeroInteractions, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.config.{AppConfig, JobConfig}
import uk.gov.hmrc.bindingtariffclassification.connector.BankHolidaysConnector
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class DaysElapsedJobTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val caseService = mock[CaseService]
  private val bankHolidaysConnector = mock[BankHolidaysConnector]
  private val appConfig = mock[AppConfig]

  override def afterEach(): Unit = {
    super.afterEach()
    reset(appConfig, caseService)
  }

  "Scheduled Job" should {

    "Configure 'Name'" in {
      new DaysElapsedJob(appConfig, caseService, bankHolidaysConnector).name shouldBe "DaysElapsed"
    }

    "Configure 'firstRunTime'" in {
      val runTime = LocalTime.of(14, 0)
      given(appConfig.daysElapsed).willReturn(JobConfig(runTime, 1))

      new DaysElapsedJob(appConfig, caseService, bankHolidaysConnector).firstRunTime shouldBe runTime
    }

    "Configure 'interval'" in {
      given(appConfig.daysElapsed).willReturn(JobConfig(LocalTime.MIDNIGHT, 1))

      new DaysElapsedJob(appConfig, caseService, bankHolidaysConnector).interval shouldBe FiniteDuration(1, DAYS)
    }

    "Execute" in {
      givenItIsNotABankHoliday()
      givenTheDateIsFixedAt("2018-12-25T12:00:00")
      given(appConfig.daysElapsed).willReturn(JobConfig(LocalTime.MIDNIGHT, 1))
      given(caseService.incrementDaysElapsed(refEq(1))).willReturn(Future.successful(2))

      await(new DaysElapsedJob(appConfig, caseService, bankHolidaysConnector).execute())

      verify(caseService).incrementDaysElapsed(1)
    }

    "Do nothing on a Saturday" in {
      givenTheDateIsFixedAt("2018-12-29T00:00:00")
      await(new DaysElapsedJob(appConfig, caseService, bankHolidaysConnector).execute())
      verifyZeroInteractions(caseService)
    }

    "Do nothing on a Sunday" in {
      givenTheDateIsFixedAt("2018-12-30T00:00:00")
      await(new DaysElapsedJob(appConfig, caseService, bankHolidaysConnector).execute())
      verifyZeroInteractions(caseService)
    }

    "Do nothing on a Bank Holiday" in {
      givenABankHolidayOn("2018-12-25")
      givenTheDateIsFixedAt("2018-12-25T00:00:00")
      await(new DaysElapsedJob(appConfig, caseService, bankHolidaysConnector).execute())
      verifyZeroInteractions(caseService)
    }

  }

  private def givenABankHolidayOn(date: String): Unit = {
    when(bankHolidaysConnector.get()(ArgumentMatchers.any[HeaderCarrier])).thenReturn(Seq(LocalDate.parse(date)))
  }

  private def givenItIsNotABankHoliday(): Unit = {
    when(bankHolidaysConnector.get()(ArgumentMatchers.any[HeaderCarrier])).thenReturn(Seq.empty)
  }

  private def givenTheDateIsFixedAt(date: String) : Unit = {
    val zone: ZoneId = ZoneOffset.UTC
    val instant = LocalDateTime.parse(date).atZone(zone).toInstant
    given(appConfig.clock).willReturn(Clock.fixed(instant, zone))
  }

}
