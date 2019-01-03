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

import java.time.LocalTime

import play.api.Logger
import uk.gov.hmrc.bindingtariffclassification.model.SchedulerRunEvent

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait ScheduledJob {

  def name: String
  def lockAndExecute: Future[Unit] = {
    val event = SchedulerRunEvent(name, expectedRunDate)

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
  protected def execute(): Future[Unit]
  def firstRunTime: LocalTime
  def interval: FiniteDuration

}
