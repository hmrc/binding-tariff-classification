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

import java.time.DayOfWeek.{SATURDAY, SUNDAY}
import java.time.{LocalDate, LocalTime}

import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.connector.BankHolidaysConnector
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.concurrent.duration._

@Singleton
class DaysElapsedJob @Inject()(appConfig: AppConfig, caseService: CaseService, bankHolidaysConnector: BankHolidaysConnector) extends ScheduledJob {

  private implicit val carrier: HeaderCarrier = HeaderCarrier()
  private lazy val jobConfig = appConfig.daysElapsed
  private lazy val weekendDays = Seq(SATURDAY, SUNDAY)
  private lazy val secondsInADay = 1.day.toSeconds

  override val name: String = "DaysElapsed"

  override def interval: FiniteDuration = jobConfig.interval

  override def firstRunTime: LocalTime = {
    jobConfig.elapseTime
  }

  override def execute(): Future[Unit] = {

    val today = LocalDate.now(appConfig.clock)

    lazy val msgPrefix = s"Scheduled Job [$name] run for day $today:"

    def isWeekend: LocalDate => Boolean = { d: LocalDate =>
      weekendDays.contains(d.getDayOfWeek)
    }

    def isBankHoliday: LocalDate => Future[Boolean] = { d: LocalDate =>
      bankHolidaysConnector.get().map(_.contains(d))
    }

    if (isWeekend(today)) {
      Logger.info(s"$msgPrefix Skipped as it is a Weekend")
      successful(())
    } else {
      isBankHoliday(today).flatMap {
        case true =>
          Logger.info(s"$msgPrefix Skipped as it is a Bank Holiday")
          successful(())
        case false =>
          val increment = jobConfig.interval.toSeconds.toDouble / secondsInADay
          caseService.incrementDaysElapsed(increment).map { modified: Int =>
            Logger.info(s"$msgPrefix Incremented the Days Elapsed for [$modified] cases")
          }
      }
    }

  }

}
