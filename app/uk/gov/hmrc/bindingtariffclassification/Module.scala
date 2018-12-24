/*
 * Copyright 2018 HM Revenue & Customs
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

import java.time.Clock

import javax.inject.{Inject, Provider, Singleton}
import play.api.inject.Binding
import play.api.{Configuration, Environment}
import reactivemongo.api.DB
import uk.gov.hmrc.bindingtariffclassification.repository.MongoDbProvider
import uk.gov.hmrc.bindingtariffclassification.scheduler.{DaysElapsedJob, ScheduledJob, Scheduler}
import uk.gov.hmrc.lock.{LockMongoRepository, LockRepository}

class Module @Inject()() extends play.api.inject.Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[ScheduledJob].to[DaysElapsedJob],
      bind[Clock].toInstance(Clock.systemDefaultZone()),
      bind[LockRepository].toProvider[LockRepositoryProvider],
      bind[Scheduler].toSelf.eagerly()
    )
  }
}

@Singleton
class LockRepositoryProvider @Inject()(mongoDbProvider: MongoDbProvider) extends Provider[LockRepository]{
  private val mongo: () => DB = () => mongoDbProvider.mongo.apply()
  override def get(): LockRepository = LockMongoRepository(mongo)
}


