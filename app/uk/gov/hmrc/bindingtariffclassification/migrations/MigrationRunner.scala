/*
 * Copyright 2023 HM Revenue & Customs
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

import com.kenshoo.play.metrics.Metrics
import uk.gov.hmrc.bindingtariffclassification.common.Logging
import uk.gov.hmrc.bindingtariffclassification.metrics.HasMetrics
import uk.gov.hmrc.bindingtariffclassification.model.JobRunEvent
import uk.gov.hmrc.bindingtariffclassification.repository._

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
    Future.sequence(migrationJobs.jobs.filter(clazz.isInstance(_)).map(run)).map(_ => ())

  private def run(job: MigrationJob): Future[Unit] = withMetricsTimerAsync(s"migration-job-${job.name}") { timer =>
    val event = JobRunEvent(job.name, ZonedDateTime.now())

    logger.info(s"Migration Job [${job.name}]: Acquiring Lock")
    migrationLockRepository.lock(event).flatMap {
      case SuccessfulInsert | UpdatingDocument =>
        logger.info(s"Migration Job [${job.name}]: Successfully acquired lock. Starting Job.")
        job.execute().map(_ => logger.info(s"Migration Job [${job.name}]: Completed Successfully"))
      case _ =>
        logger.info(s"Migration Job [${job.name}]: Failed to acquire Lock. It may have been running already.")
        timer.completeWithFailure()
        successful(())
    } recover {
      case t: Throwable =>
        logger.error(s"Migration Job [${job.name}]: Failed", t)
        logger.info(s"Migration Job [${job.name}]: Attempting to rollback")
        job
          .rollback()
          .flatMap { _ =>
            logger.info(s"Migration Job [${job.name}]: Rollback Completed Successfully")
            migrationLockRepository.rollback(event)
          }
          .recover {
            case t: Throwable =>
              logger.error(s"Migration Job [${job.name}]: Rollback Failed", t)
              timer.completeWithFailure()
          }
    }
  }
}
