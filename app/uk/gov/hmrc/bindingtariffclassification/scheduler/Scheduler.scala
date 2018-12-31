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

import java.time.Instant
import java.time.temporal.{ChronoUnit, Temporal}
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import javax.inject.Inject
import play.api.Logger
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.SchedulerRunEvent
import uk.gov.hmrc.bindingtariffclassification.repository.SchedulerLockRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

class Scheduler @Inject()(actorSystem: ActorSystem, appConfig: AppConfig, schedulerLockRepository: SchedulerLockRepository, job: ScheduledJob) {

  Logger.info(s"Scheduled Job [${job.name}]: Scheduling to run at [${job.firstRunDate}] with interval [${job.interval.length} ${job.interval.unit}]")
  actorSystem.scheduler.schedule(durationUntil(job.firstRunDate), job.interval, new Runnable(){
    override def run(): Unit = {
      val event = SchedulerRunEvent(job.name, expectedRunDate)

      Logger.info(s"Scheduled Job [${job.name}]: Acquiring Lock")
      schedulerLockRepository.lock(event) map {
        case true =>
          Logger.info(s"Scheduled Job [${job.name}]: Successfully acquired lock, Starting Job.")
          job.execute() map { _ =>
            Logger.info(s"Scheduled Job [${job.name}]: Completed Successfully")
          } recover {
            case t: Throwable => Logger.error(s"Scheduled Job [${job.name}]: Failed", t)
          }
        case false => Logger.info(s"Scheduled Job [${job.name}]: Failed to acquire Lock. It may be running elsewhere.")
      }
    }
  })

  private def expectedRunDate: Instant = {
    /*
    Calculates the instant in time the job theoretically SHOULD run at.
    This needs to be consistent among multiple instances of the service (for locking) and is not necessarily the time
    the job ACTUALLY runs at (depending on how accurate the scheduler timer is).
    */
    val now = Instant.now(appConfig.clock)
    if(!job.firstRunDate.isBefore(now)) {
      job.firstRunDate
    } else {
      val intervals: Long = Math.floor((now.toEpochMilli - job.firstRunDate.toEpochMilli) / job.interval.toMillis).toLong
      job.firstRunDate.plusMillis(intervals * job.interval.toMillis)
    }
  }

  def durationUntil(instant: Instant): FiniteDuration = {
    val now = Instant.now(appConfig.clock)
    if(now.isBefore(instant)) {
      FiniteDuration(now.until(instant, ChronoUnit.SECONDS), TimeUnit.SECONDS)
    } else {
      FiniteDuration(0, TimeUnit.SECONDS)
    }
  }

}
