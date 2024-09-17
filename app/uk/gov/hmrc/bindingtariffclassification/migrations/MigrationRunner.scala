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

import cats.implicits._
import uk.gov.hmrc.bindingtariffclassification.common.Logging
import uk.gov.hmrc.bindingtariffclassification.metrics.HasMetrics
import uk.gov.hmrc.bindingtariffclassification.model.JobRunEvent
import uk.gov.hmrc.bindingtariffclassification.repository.MigrationLockRepository
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.time.ZonedDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait MigrationResult
case object DeletedEvent extends MigrationResult
case object RollbackFailure extends MigrationResult
case object TimerCompleted extends MigrationResult
case object JobExecuted extends MigrationResult
case object AlreadyRanBefore extends MigrationResult

@Singleton
class MigrationRunner @Inject() (
  migrationLockRepository: MigrationLockRepository,
  migrationJobs: MigrationJobs,
  val metrics: Metrics
)(implicit ec: ExecutionContext)
    extends Logging
    with HasMetrics {

  def trigger(migrationJob: MigrationJob): Future[Option[MigrationResult]] =
    migrationJobs.jobs.find(_ == migrationJob).traverse(run)

  def jobNameLog(job: MigrationJob): String = s"Migration Job [${job.name}]"

  def run(job: MigrationJob): Future[MigrationResult] =
    withMetricsTimerAsync(s"migration-job-${job.name}") { timer =>
      val databasePastJob = migrationLockRepository.findOne(job.name)

      databasePastJob.flatMap {
        case Some(dbJob) =>
          logger.info(
            s"[MigrationRunner][run] ${jobNameLog(job)}: Job found! Last successful run was on ${dbJob.runDate}, Skip running the job"
          )
          Future(AlreadyRanBefore)
        case None =>
          executeJob(job, timer)
      }
    }

  def executeJob(job: MigrationJob, timer: MetricsTimer): Future[MigrationResult] = {
    val event = JobRunEvent(job.name, ZonedDateTime.now())
    logger.info(s"[MigrationRunner][executeJob] ${jobNameLog(job)}: Acquiring Lock")

    migrationLockRepository
      .lock(event)
      .flatMap {
        case true =>
          logger.info(s"[MigrationRunner][executeJob] ${jobNameLog(job)}: Successfully acquired lock. Starting Job.")
          job
            .execute()
            .flatMap { _ =>
              logger.info(s"[MigrationRunner][executeJob] ${jobNameLog(job)}: Completed Successfully")
              Future(JobExecuted)
            }
        case false =>
          logger.info(s"[MigrationRunner][executeJob] ${jobNameLog(job)}: Failed to acquire Lock. It may have been running already.")
          timer.completeWithFailure()
          Future(TimerCompleted)
      }
      .recoverWith { case t: Throwable =>
        logger.error(s"[MigrationRunner][executeJob] ${jobNameLog(job)}: Failed to execute job", t)
        rollback(job, event, timer)
      }
  }

  def rollback(job: MigrationJob, event: JobRunEvent, timer: MetricsTimer): Future[MigrationResult] = {
    logger.info(s"[MigrationRunner][rollback] ${jobNameLog(job)}: Attempting to rollback")

    job
      .rollback()
      .map { _ =>
        logger.info(s"[MigrationRunner][rollback] ${jobNameLog(job)}: Rollback Completed Successfully")
        migrationLockRepository.delete(event)
        DeletedEvent
      }
      .recover { case throwable: Throwable =>
        logger.error(s"[MigrationRunner][rollback] ${jobNameLog(job)}: Rollback Failed", throwable)
        timer.completeWithFailure()
        RollbackFailure
      }
  }

}
