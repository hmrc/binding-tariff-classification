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
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.migrations.{DeletedEvent, MigrationJobs, MigrationRunner}
import uk.gov.hmrc.bindingtariffclassification.model.JobRunEvent
import util.TestMetrics

import scala.concurrent.Future

class MigrationRunner3Spec extends BaseSpec with ScalaFutures with MockitoSugar {

  override def beforeAll(): Unit = {
    super.beforeAll()
    reset(mockMigrationJob, mockMigrationLockMongoRepository)
    clearAllCaches()
    clearInvocations(mockMigrationJob, mockMigrationLockMongoRepository)
  }

  def runner: MigrationRunner =
    new MigrationRunner(mockMigrationLockMongoRepository, MigrationJobs(Set(mockMigrationJob)), new TestMetrics)

  class TestSetup(jobName: String) {
    when(mockMigrationJob.name).thenReturn(jobName)
    when(mockMigrationLockMongoRepository.findOne(any[String])).thenReturn(Future.successful(None))
  }

  "MigrationRunner3" should {

    "rollback the job on a failure, deleting the event" in new TestSetup("MigrationJobTest3") {

      when(mockMigrationLockMongoRepository.findOne(any[String])).thenReturn(Future.successful(None))
      when(mockMigrationLockMongoRepository.lock(any[JobRunEvent])).thenReturn(Future.successful(true))
      when(mockMigrationJob.execute())
        .thenReturn(Future.failed(new RuntimeException("[MigrationJob] test execute() failed")))
      when(mockMigrationJob.rollback()).thenReturn(Future.successful(()))

      // Run the trigger and handle future result, we fall into the delete event block within rollback() method
      whenReady(runner.trigger(mockMigrationJob)) { result =>
        result shouldBe Some(DeletedEvent)
      }

      // Verify interactions
      verify(mockMigrationJob, atLeastOnce()).name
      verify(mockMigrationJob, times(1)).execute()
      verify(mockMigrationJob, times(1)).rollback()
    }
  }
}
