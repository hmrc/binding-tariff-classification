/*
 * Copyright 2025 HM Revenue & Customs
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
import org.mockito.Mockito.when
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.model.JobRunEvent
import uk.gov.hmrc.bindingtariffclassification.repository.MigrationLockRepository
import util.TestMetrics

import java.time.ZonedDateTime.now
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.failed

class MigrationRunnerTest extends BaseSpec {

  class Test {
    private def jobName                              = "MigrationJobTest"
    val migrationJob: MigrationJob                   = mock[MigrationJob]
    val migrationRepository: MigrationLockRepository = mock[MigrationLockRepository]

    when(migrationJob.name).thenReturn(jobName)

    def givenThereWasMigrationRan(): Unit =
      when(migrationRepository.findOne(any[String]))
        .thenReturn(Future.successful(Some(JobRunEvent(jobName, now()))))

    def givenThereWasNoMigrationRan(): Unit =
      when(migrationRepository.findOne(any[String])).thenReturn(Future.successful(None))

    def givenTheLockSucceeds(): Unit =
      when(migrationRepository.lock(any[JobRunEvent])).thenReturn(Future.successful(true))

    def givenTheLockFails(): Unit =
      when(migrationRepository.lock(any[JobRunEvent])).thenReturn(Future.successful(false))

    def givenRollbackSucceeds(): Unit =
      when(migrationRepository.delete(any[JobRunEvent])).thenReturn(Future.successful(()))

    def givenAmendDateOfExtractJobSucceeds(): Unit =
      when(migrationJob.execute()).thenReturn(Future.successful(()))

    def givenAmendDateOfExtractJobFails(): Unit =
      when(migrationJob.execute()).thenReturn(failed(new RuntimeException("test execute() failed")))

    def givenAmendDateOfExtractJobRollbackSucceeds(): Unit =
      when(migrationJob.rollback()).thenReturn(Future.successful(()))

    def givenAmendDateOfExtractJobRollbackFails(): Unit =
      when(migrationJob.rollback()).thenReturn(failed(new RuntimeException("test rollback() failed")))

    val runner = new MigrationRunner(migrationRepository, MigrationJobs(Set(migrationJob)), new TestMetrics)
  }

  "MigrationRunner" should {

    "execute the job by class" in new Test {
      givenThereWasNoMigrationRan()
      givenTheLockSucceeds()
      givenAmendDateOfExtractJobSucceeds()

      await(runner.trigger(migrationJob)) shouldBe Some(JobExecuted)
    }

    "not execute the job if the lock fails" in new Test {
      givenThereWasNoMigrationRan()
      givenTheLockFails()

      await(runner.trigger(migrationJob)) shouldBe Some(TimerCompleted)
    }

    "rollback the job on a failure" in new Test {
      givenThereWasNoMigrationRan()
      givenTheLockSucceeds()
      givenRollbackSucceeds()
      givenAmendDateOfExtractJobFails()
      givenAmendDateOfExtractJobRollbackSucceeds()

      await(runner.trigger(migrationJob)) shouldBe Some(DeletedEvent)
    }

    "rollback failed when job failed" in new Test {
      givenThereWasNoMigrationRan()
      givenTheLockSucceeds()
      givenRollbackSucceeds()
      givenAmendDateOfExtractJobFails()
      givenAmendDateOfExtractJobRollbackFails()

      await(runner.trigger(migrationJob)) shouldBe Some(RollbackFailure)
    }

    "findOne had a job ran before" in new Test {
      givenThereWasMigrationRan()
      givenTheLockSucceeds()
      givenAmendDateOfExtractJobSucceeds()

      await(runner.trigger(migrationJob)) shouldBe Some(AlreadyRanBefore)
    }

    "findOne had no job ran before" in new Test {
      givenThereWasNoMigrationRan()
      givenTheLockSucceeds()
      givenAmendDateOfExtractJobSucceeds()

      await(runner.trigger(migrationJob)) shouldBe Some(JobExecuted)
    }
  }
}
