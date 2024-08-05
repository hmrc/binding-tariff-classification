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

package uk.gov.hmrc.bindingtariffclassification.migrations.migrationRunner

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.migrations.{MigrationJobs, MigrationRunner}
import uk.gov.hmrc.bindingtariffclassification.model.JobRunEvent
import util.TestMetrics

import java.time.ZonedDateTime.now
import scala.concurrent.{ExecutionContext, Future}

class MigrationRunner5Spec extends BaseSpec {

  override def beforeAll(): Unit = {
    reset()
    clearAllCaches()
    clearInvocations(mockMigrationJob, mockMigrationLockMongoRepository)
  }

  def runner: MigrationRunner =
    new MigrationRunner(mockMigrationLockMongoRepository, MigrationJobs(Set(mockMigrationJob)), new TestMetrics)

  class TestSetup(jobName: String) {

    when(mockMigrationJob.name).thenReturn(jobName)
    when(mockMigrationLockMongoRepository.findOne(any[String])).thenReturn(Future(None))
  }

  "MigrationRunner5" when {

    "findOne had a job ran before, test method execution" in new TestSetup("MigrationJobTest5") {

      when(mockMigrationLockMongoRepository.findOne(any[String]))
        .thenReturn(Future.successful(Some(JobRunEvent("MigrationJobTest4", now()))))

      when(mockMigrationLockMongoRepository.lock(any[JobRunEvent])).thenReturn(Future.successful(true))

      when(mockMigrationJob.execute()).thenReturn(Future(()))

      await(runner.trigger(mockMigrationJob))

      verify(mockMigrationJob, atLeastOnce()).name
      verify(mockMigrationJob, never()).execute()
      verify(mockMigrationJob, never()).rollback()
    }
  }
}
