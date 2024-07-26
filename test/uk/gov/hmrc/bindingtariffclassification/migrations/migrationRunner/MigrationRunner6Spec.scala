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
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.migrations.{MigrationJob, MigrationJobs, MigrationRunner}
import uk.gov.hmrc.bindingtariffclassification.repository.MigrationLockMongoRepository
import util.TestMetrics

import scala.concurrent.Future

class MigrationRunner6Spec extends BaseSpec with BeforeAndAfterAll with MockitoSugar with ScalaFutures {

  override def beforeAll(): Unit = {
    super.beforeAll() // Ensure BaseSpec's beforeAll is called
    reset(mockMigrationJob, mockMigrationLockMongoRepository)
    clearAllCaches()
    clearInvocations(mockMigrationJob, mockMigrationLockMongoRepository)
  }

  def runner: MigrationRunner =
    new MigrationRunner(
      new MigrationLockMongoRepository(fakeMongoComponent),
      MigrationJobs(Set(mockMigrationJob)),
      new TestMetrics
    )

  "MigrationRunner6" when {

    "no job found to run, test no migration is executed" in {

      val resultFuture = runner.trigger(mockMigrationJob)

      whenReady(resultFuture) { _ =>
        verify(mockMigrationJob, atLeastOnce()).name
        verify(mockMigrationJob, never()).execute()
        verify(mockMigrationJob, never()).rollback()
      }
    }
  }
}
