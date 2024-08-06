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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{atLeastOnce, never, verify, when}
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.model.JobRunEvent
import uk.gov.hmrc.bindingtariffclassification.repository.MigrationLockRepository
import util.TestMetrics

import java.time.ZonedDateTime.now
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

class MigrationRunnerTest extends BaseSpec {

  class Test {
    private def jobName                              = "MigrationJobTest"
    val migrationJob: MigrationJob                   = mock[MigrationJob]
    val migrationRepository: MigrationLockRepository = mock[MigrationLockRepository]

    when(migrationJob.name).thenReturn(jobName)

    def givenThereWasMigrationRan(): Unit =
      when(migrationRepository.findOne(any[String]))
        .thenReturn(successful(Some(JobRunEvent(jobName, now()))))

    def givenThereWasNoMigrationRan(): Unit =
      when(migrationRepository.findOne(any[String])).thenReturn(successful(None))

    def givenTheLockSucceeds(): Unit =
      when(migrationRepository.lock(any[JobRunEvent])).thenReturn(successful(true))

    def givenTheLockFails(): Unit =
      when(migrationRepository.lock(any[JobRunEvent])).thenReturn(successful(false))

    def givenRollbackSucceeds(): Unit =
      when(migrationRepository.delete(any[JobRunEvent])).thenReturn(successful(()))

    def givenAmendDateOfExtractJobSucceeds(): Unit =
      when(migrationJob.execute()).thenReturn(successful(()))

    def givenAmendDateOfExtractJobFails(): Unit =
      when(migrationJob.execute()).thenReturn(failed(new RuntimeException("test execute() failed")))

    def givenAmendDateOfExtractJobRollbackSucceeds(): Unit =
      when(migrationJob.rollback()).thenReturn(successful(()))

    def givenAmendDateOfExtractJobRollbackFails(): Unit =
      when(migrationJob.rollback()).thenReturn(failed(new RuntimeException("test rollback() failed")))

    val runner = new MigrationRunner(migrationRepository, MigrationJobs(Set(migrationJob)), new TestMetrics)
  }

  "MigrationRunner" should {

    "execute the job by class" in new Test {
      givenThereWasNoMigrationRan()
      givenTheLockSucceeds()
      givenAmendDateOfExtractJobSucceeds()

      await(runner.trigger(migrationJob.getClass))

      verify(migrationJob).execute()
      verify(migrationJob, never()).rollback()
    }

    "not execute the job if the lock fails" in new Test {
      givenThereWasNoMigrationRan()
      givenTheLockFails()

      await(runner.trigger(migrationJob.getClass))

      verify(migrationJob, never()).execute()
      verify(migrationJob, never()).rollback()
    }

    "rollback the job on a failure" in new Test {
      givenThereWasNoMigrationRan()
      givenTheLockSucceeds()
      givenRollbackSucceeds()
      givenAmendDateOfExtractJobFails()
      givenAmendDateOfExtractJobRollbackSucceeds()

      await(runner.trigger(migrationJob.getClass))

      verify(migrationJob, atLeastOnce()).name
      verify(migrationJob).execute()
      verify(migrationJob).rollback()
    }

    "rollback failed when job failed" in new Test {
      givenThereWasNoMigrationRan()
      givenTheLockSucceeds()
      givenRollbackSucceeds()
      givenAmendDateOfExtractJobFails()
      givenAmendDateOfExtractJobRollbackFails()

      await(runner.trigger(migrationJob.getClass))

      verify(migrationJob, atLeastOnce()).name
      verify(migrationJob).execute()
      verify(migrationJob).rollback()
    }

    "findOne had a job ran before" in new Test {
      givenThereWasMigrationRan()
      givenTheLockSucceeds()
      givenAmendDateOfExtractJobSucceeds()

      await(runner.trigger(migrationJob.getClass))

      verify(migrationJob, atLeastOnce()).name
      verify(migrationJob, never()).execute()
      verify(migrationJob, never()).rollback()
    }

    "findOne had no job ran before" in new Test {
      givenThereWasNoMigrationRan()
      givenTheLockSucceeds()
      givenAmendDateOfExtractJobSucceeds()

      await(runner.trigger(migrationJob.getClass))

      verify(migrationJob, atLeastOnce()).name
      verify(migrationJob).execute()
      verify(migrationJob, never()).rollback()
    }

    "no job found to run" in new Test {
      await(runner.trigger(classOf[String]))

      verify(migrationJob, never()).name
      verify(migrationJob, never()).execute()
      verify(migrationJob, never()).rollback()
    }

  }

}
