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

import akka.actor.ActorSystem
import javax.inject.Inject
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global

class Scheduler @Inject()(actorSystem: ActorSystem, job: ScheduledJob) {

  Logger.info(s"Scheduling Job [${job.name}] to run in [${job.initialDelay.length} ${job.initialDelay.unit}] with interval [${job.interval.length} ${job.interval.unit}]")
  actorSystem.scheduler.schedule(job.initialDelay, job.interval) {
    job.execute
      .map(result => Logger.info(s"Job [${job.name}] completed with result [$result]"))
  }

}
