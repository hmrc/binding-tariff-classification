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

package uk.gov.hmrc.bindingtariffclassification.component

import com.kenshoo.play.metrics.Metrics
import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.bindingtariffclassification.model.{Case, Event, Sequence}
import uk.gov.hmrc.bindingtariffclassification.repository.{CaseMongoRepository, EventMongoRepository, SequenceMongoRepository}
import uk.gov.hmrc.bindingtariffclassification.scheduler.ScheduledJobs
import util.TestMetrics

import scala.concurrent.Await.result
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

abstract class BaseFeatureSpec
    extends FeatureSpec
    with Matchers
    with GivenWhenThen
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with BeforeAndAfterAll {

  override lazy val app: Application = GuiceApplicationBuilder()
    .configure("mongodb.uri" -> "mongodb://localhost:27017/test-ClassificationMongoRepositoryTest")
    .overrides(bind[Metrics].toInstance(new TestMetrics))
    .build()

  protected val timeout: FiniteDuration = 5.seconds

  protected lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  protected lazy val apiTokenKey = "X-Api-Token"

  private lazy val caseStore: CaseMongoRepository         = app.injector.instanceOf[CaseMongoRepository]
  private lazy val eventStore: EventMongoRepository       = app.injector.instanceOf[EventMongoRepository]
  private lazy val sequenceStore: SequenceMongoRepository = app.injector.instanceOf[SequenceMongoRepository]
  private lazy val scheduledJobStores: Iterable[LockRepository] =
    app.injector.instanceOf[ScheduledJobs].jobs.map(_.repo)

  private def dropStores(): Unit = {
    result(caseStore.drop, timeout)
    result(eventStore.drop, timeout)
    result(sequenceStore.drop, timeout)
    result(Future.traverse(scheduledJobStores)(_.drop), timeout)
  }

  private def ensureStoresIndexes(): Unit = {
    result(caseStore.ensureIndexes, timeout)
    result(eventStore.ensureIndexes, timeout)
    result(sequenceStore.ensureIndexes, timeout)
    result(Future.traverse(scheduledJobStores)(_.ensureIndexes), timeout)
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    dropStores()
    ensureStoresIndexes()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    dropStores()
  }

  protected def storeCases(cases: Case*): Seq[Case] =
    cases.map { c: Case =>
      // for simplicity encryption is not tested here (because disabled in application.conf)
      result(caseStore.insert(c), timeout)
    }

  protected def storeEvents(events: Event*): Seq[Event] =
    events.map(e => result(eventStore.insert(e), timeout))

  protected def storeSequences(sequences: Sequence*): Seq[Sequence] =
    sequences.map(s => result(sequenceStore.insert(s), timeout))

  protected def caseStoreSize: Int =
    result(caseStore.mongoCollection.count(), timeout)

  protected def eventStoreSize: Int =
    result(eventStore.mongoCollection.count(), timeout)

  protected def getCase(ref: String): Option[Case] =
    // for simplicity decryption is not tested here (because disabled in application.conf)
    result(caseStore.getByReference(ref), timeout)

}
