/*
 * Copyright 2022 HM Revenue & Customs
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

import com.kenshoo.play.metrics.Metrics
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder.newJob
import org.quartz.TriggerBuilder.newTrigger
import org.quartz.impl.StdSchedulerFactory
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.bindingtariffclassification.common.Logging
import uk.gov.hmrc.bindingtariffclassification.metrics.HasMetrics

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Scheduler @Inject() (
  lifecycle: ApplicationLifecycle,
  jobFactory: ScheduledJobFactory,
  scheduledJobs: ScheduledJobs,
  val metrics: Metrics
)(implicit ec: ExecutionContext)
    extends Logging
    with HasMetrics {

  val quartz = StdSchedulerFactory.getDefaultScheduler

  quartz.setJobFactory(jobFactory)

  lifecycle.addStopHook(() => Future(quartz.shutdown()))

  val (enabledJobs, disabledJobs) = scheduledJobs.jobs.partition(_.enabled)

  disabledJobs.foreach(job => logger.warn(s"Scheduled job [${job.name}] is disabled"))

  enabledJobs.foreach { job =>
    val detail = newJob(job.getClass)
      .withIdentity(job.name)
      .build()

    val scheduleDescription = job.schedule.getCronExpression

    val schedule = CronScheduleBuilder.cronSchedule(job.schedule)

    val trigger = newTrigger()
      .forJob(detail)
      .withSchedule(schedule)
      .build()

    logger.info(s"Scheduling job [${job.name}] with schedule [$scheduleDescription]")

    quartz.scheduleJob(detail, trigger)
  }

  quartz.start()
}
