/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.migrations

import uk.gov.hmrc.bindingtariffclassification.common.Logging
import uk.gov.hmrc.bindingtariffclassification.metrics.HasMetrics
import uk.gov.hmrc.bindingtariffclassification.model.JobRunEvent
import uk.gov.hmrc.bindingtariffclassification.repository.MigrationLockRepository
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.time.ZonedDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MigrationRunner @Inject() (
  migrationLockRepository: MigrationLockRepository,
  migrationJobs: MigrationJobs,
  val metrics: Metrics
)(implicit ec: ExecutionContext)
    extends Logging
    with HasMetrics {
  def trigger[T](clazz: Class[T]): Future[Unit] =
    migrationJobs.jobs.find(clazz.isInstance(_)).map(run).getOrElse(successful(()))

  private def jobNameLog(job: MigrationJob): String = s"Migration Job [${job.name}]"

  private def run(job: MigrationJob): Future[Unit] = withMetricsTimerAsync(s"migration-job-${job.name}") { timer =>
    val databasePastJob = migrationLockRepository.findOne(job.name)

    databasePastJob.map {
      case Some(dbJob) =>
        logger.info(s"${jobNameLog(job)}: Job found! Last successful run was on ${dbJob.runDate}, Skip running the job")
        successful(())
      case None =>
        executeJob(job, timer)
    }
  }

  private def rollback(job: MigrationJob, event: JobRunEvent, timer: MetricsTimer): Future[Unit] = {
    logger.info(s"${jobNameLog(job)}: Attempting to rollback")

    job
      .rollback()
      .flatMap { _ =>
        logger.info(s"${jobNameLog(job)}: Rollback Completed Successfully")
        migrationLockRepository.delete(event)
      }
      .recover { case throwable: Throwable =>
        logger.error(s"${jobNameLog(job)}: Rollback Failed", throwable)
        timer.completeWithFailure()
      }
  }

  private def executeJob(job: MigrationJob, timer: MetricsTimer): Future[Unit] = {
    val event = JobRunEvent(job.name, ZonedDateTime.now())

    logger.info(s"${jobNameLog(job)}: Acquiring Lock")
    migrationLockRepository.lock(event).flatMap {
      case true =>
        logger.info(s"${jobNameLog(job)}: Successfully acquired lock. Starting Job.")
        job.execute().map { _ =>
          logger.info(s"${jobNameLog(job)}: Completed Successfully")
        } recover { case t: Throwable =>
          logger.error(s"${jobNameLog(job)}: Failed to execute job", t)
          rollback(job, event, timer).flatMap(_ => successful(()))
        }
      case false =>
        logger.info(s"${jobNameLog(job)}: Failed to acquire Lock. It may have been running already.")
        timer.completeWithFailure()
        successful(())
    }
  }

}
