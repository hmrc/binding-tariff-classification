/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification

import play.api.inject.Binding
import play.api.{Configuration, Environment}
import uk.gov.hmrc.bindingtariffclassification.crypto.LocalCrypto
import uk.gov.hmrc.bindingtariffclassification.migrations.{AddKeywordsMigrationJob, AmendDateOfExtractMigrationJob, MigrationJobs}
import uk.gov.hmrc.bindingtariffclassification.repository.{CaseMongoRepository, CaseRepository, EncryptedCaseMongoRepository}
import uk.gov.hmrc.bindingtariffclassification.scheduler._
import uk.gov.hmrc.crypto.CompositeSymmetricCrypto

import javax.inject.{Inject, Provider}

class Module extends play.api.inject.Module {

  def isMongoEncryptionEnabled(configuration: Configuration): Boolean =
    configuration.get[Boolean]("mongodb.encryption.enabled")

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    val repositoryBinding: Binding[CaseRepository] = if (isMongoEncryptionEnabled(configuration)) {
      bind[CaseRepository].to[EncryptedCaseMongoRepository]
    } else {
      bind[CaseRepository].to[CaseMongoRepository]
    }

    Seq(
      bind[CompositeSymmetricCrypto].to(classOf[LocalCrypto]),
      bind[ScheduledJobs].toProvider[ScheduledJobProvider],
      bind[MigrationJobs].toProvider[MigrationJobProvider],
      bind[Scheduler].toSelf.eagerly(),
      repositoryBinding
    )
  }

}

class ScheduledJobProvider @Inject() (
  activeDaysElapsedJob: ActiveDaysElapsedJob,
  referredDaysElapsedJob: ReferredDaysElapsedJob,
  fileStoreCleanupJob: FileStoreCleanupJob
) extends Provider[ScheduledJobs] {
  override def get(): ScheduledJobs =
    ScheduledJobs(Set(activeDaysElapsedJob, referredDaysElapsedJob, fileStoreCleanupJob))
}

class MigrationJobProvider @Inject() (
  amendDateOfExtractMigration: AmendDateOfExtractMigrationJob,
  addKeywordsMigration: AddKeywordsMigrationJob
) extends Provider[MigrationJobs] {
  override def get(): MigrationJobs = MigrationJobs(Set(amendDateOfExtractMigration, addKeywordsMigration))
}
