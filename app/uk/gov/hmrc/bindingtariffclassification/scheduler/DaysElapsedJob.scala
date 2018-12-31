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
import java.util.concurrent.TimeUnit

import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.connector.BankHolidaysConnector
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

@Singleton
class DaysElapsedJob @Inject()(appConfig: AppConfig, caseService: CaseService, bankHolidaysConnector: BankHolidaysConnector) extends ScheduledJob {

  private implicit val carrier: HeaderCarrier = HeaderCarrier()

  private lazy val jobConfig = appConfig.daysElapsed

  override def name: String = "DaysElapsed"

  override def interval: FiniteDuration = FiniteDuration(jobConfig.intervalDays, TimeUnit.DAYS)

  override def execute(): Future[Unit] = {
    ifTodayIsAWorkingDay {
      caseService.incrementDaysElapsed(jobConfig.intervalDays)
        .map(count => Logger.info(s"Scheduled Job [$name]: Incremented the Days Elapsed for [$count] cases."))
    }
  }

  private def ifTodayIsAWorkingDay(function: => Future[Unit]): Future[Unit] = {
    val today = LocalDate.now(appConfig.clock)
    val dayOfTheWeek = today.getDayOfWeek
    if (dayOfTheWeek == DayOfWeek.SATURDAY || dayOfTheWeek == DayOfWeek.SUNDAY) {
      Logger.info(s"Scheduled Job [$name]: Skipping today as it is a Weekend")
      Future.successful((): Unit)
    } else {
      bankHolidaysConnector.get()
        .map(_.contains(today))
        .flatMap {
          case true =>
            Logger.info(s"Scheduled Job [$name]: Skipping today as it is a Bank Holiday")
            Future.successful((): Unit)
          case false =>
            function
        }
    }
  }

  override def firstRunDate: Instant = {
    val currentTime = LocalDateTime.now(appConfig.clock)

    val nextRunTime: LocalTime = jobConfig.elapseTime
    val nextRunDateTimeToday = nextRunTime.atDate(LocalDate.now(appConfig.clock))
    val nextRunDateTimeTomorrow = nextRunTime.atDate(LocalDate.now(appConfig.clock).plusDays(1))
    val nextRunDateTime = if (nextRunDateTimeToday.isAfter(currentTime)) nextRunDateTimeToday else nextRunDateTimeTomorrow
    nextRunDateTime.atZone(appConfig.clock.getZone).toInstant
  }

}
