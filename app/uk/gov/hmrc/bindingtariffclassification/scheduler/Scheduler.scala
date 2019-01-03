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

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime}
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import javax.inject._
import play.api.Logger
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.SchedulerRunEvent
import uk.gov.hmrc.bindingtariffclassification.repository.SchedulerLockRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

@Singleton
class Scheduler @Inject()(actorSystem: ActorSystem, appConfig: AppConfig, schedulerLockRepository: SchedulerLockRepository, job: ScheduledJob) {

  Logger.info(s"Scheduling job [${job.name}] to run periodically at [${job.firstRunTime}] with interval [${job.interval.length} ${job.interval.unit}]")
  actorSystem.scheduler.schedule(durationUntil(nextDateWithTime(job.firstRunTime)), job.interval, new Runnable() {

    override def run(): Unit = {

      val event = SchedulerRunEvent(job.name, expectedRunDate)

      Logger.info(s"Scheduled Job [${job.name}]: Acquiring Lock")
      schedulerLockRepository.lock(event).map {
        case true =>
          Logger.info(s"Scheduled Job [${job.name}]: Successfully acquired lock, Starting Job.")
          job.execute().map { _ =>
            Logger.info(s"Scheduled Job [${job.name}]: Completed Successfully")
          } recover {
            case t: Throwable => Logger.error(s"Scheduled Job [${job.name}]: Failed", t)
          }
        case false => Logger.info(s"Scheduled Job [${job.name}]: Failed to acquire Lock. It may be running elsewhere.")
      }.map( _ => ())
    }
  })

  private def expectedRunDate: Instant = {
    /*
    Calculates the instant in time the job theoretically SHOULD run at.
    This needs to be consistent among multiple instances of the service (for locking) and is not necessarily the time
    the job ACTUALLY runs at (depending on how accurate the scheduler timer is).
    */
    val now = Instant.now(appConfig.clock)

    val firstRunDate = nextDateWithTime(job.firstRunTime)

    if (firstRunDate.isBefore(now)) {
      val intervals: Long = Math.floor((now.toEpochMilli - firstRunDate.toEpochMilli) / job.interval.toMillis).toLong
      firstRunDate.plusMillis(intervals * job.interval.toMillis)
    } else {
      firstRunDate
    }
  }

  private def nextDateWithTime(nextRunTime: LocalTime): Instant = {
    val currentDateTime = LocalDateTime.now(appConfig.clock)
    val currentTime = currentDateTime.toLocalTime

    def nextRunDateTime: LocalDateTime = {
      if (nextRunTime.isBefore(currentTime)) {
        nextRunTime.atDate(LocalDate.now(appConfig.clock).plusDays(1))
      } else {
        nextRunTime.atDate(LocalDate.now(appConfig.clock))
      }
    }

    nextRunDateTime.atZone(appConfig.clock.getZone).toInstant
  }

  private def durationUntil(date: Instant): FiniteDuration = {
    val now = Instant.now(appConfig.clock)

    if (now.isBefore(date)) FiniteDuration(now.until(date, ChronoUnit.SECONDS), TimeUnit.SECONDS)
    else FiniteDuration(0, TimeUnit.SECONDS)
  }

}
