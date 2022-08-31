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

package uk.gov.hmrc.bindingtariffclassification.migrations

import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.`given`
import org.mockito.Mockito.{never, reset, verify}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import play.api.test.Helpers._
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.model.JobRunEvent
import uk.gov.hmrc.bindingtariffclassification.repository.MigrationLockRepository
import util.TestMetrics

import scala.concurrent.Future.{failed, successful}

class MigrationRunnerTest extends BaseSpec with BeforeAndAfterEach with Eventually {

  private val amendDateOfExtractJob = mock[AmendDateOfExtractMigrationJob]
  private val migrationRepository   = mock[MigrationLockRepository]

  private def givenTheLockSucceeds(): Unit =
    given(migrationRepository.lock(any[JobRunEvent])).willReturn(successful(true))

  private def givenTheLockFails(): Unit =
    given(migrationRepository.lock(any[JobRunEvent])).willReturn(successful(false))

  private def givenRollbackSucceeds(): Unit =
    given(migrationRepository.rollback(any[JobRunEvent])).willReturn(successful(()))

  private def givenAmendDateOfExtractJobSucceeds(): Unit =
    given(amendDateOfExtractJob.execute()).willReturn(successful(()))

  private def givenAmendDateOfExtractJobFails(): Unit =
    given(amendDateOfExtractJob.execute()).willReturn(failed(new RuntimeException()))

  private def givenAmendDateOfExtractJobRollbackSucceeds(): Unit =
    given(amendDateOfExtractJob.rollback()).willReturn(successful(()))

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(amendDateOfExtractJob.name) willReturn "AmendDateOfExtract"
  }
  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(migrationRepository, amendDateOfExtractJob)
  }

  "MigrationRunner" should {

    "execute the job by class" in withRunner { runner =>
      givenTheLockSucceeds()
      givenAmendDateOfExtractJobSucceeds()

      await(runner.trigger(amendDateOfExtractJob.getClass))

      verify(amendDateOfExtractJob).execute()
      verify(amendDateOfExtractJob, never()).rollback()
    }

    "not execute the job if the lock fails" in withRunner { runner =>
      givenTheLockFails()

      await(runner.trigger(amendDateOfExtractJob.getClass))

      verify(amendDateOfExtractJob, never()).execute()
      verify(amendDateOfExtractJob, never()).rollback()
    }

    "rollback the job on a failure" in withRunner { runner =>
      givenTheLockSucceeds()
      givenRollbackSucceeds()
      givenAmendDateOfExtractJobFails()
      givenAmendDateOfExtractJobRollbackSucceeds()

      await(runner.trigger(amendDateOfExtractJob.getClass))

      verify(amendDateOfExtractJob).execute()
      verify(amendDateOfExtractJob).rollback()
    }

  }

  private def withRunner(test: MigrationRunner => Unit): Unit = {
    val runner = new MigrationRunner(migrationRepository, MigrationJobs(Set(amendDateOfExtractJob)), new TestMetrics)
    test(runner)
  }

}
