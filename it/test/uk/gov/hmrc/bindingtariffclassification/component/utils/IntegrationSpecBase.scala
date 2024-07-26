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

package uk.gov.hmrc.bindingtariffclassification.component.utils

import com.codahale.metrics.Timer
import com.mongodb.WriteConcern
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.when
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Environment, Mode}
import uk.gov.hmrc.bindingtariffclassification.metrics.HasMetrics
import uk.gov.hmrc.bindingtariffclassification.model.{Case, Event, Sequence}
import uk.gov.hmrc.bindingtariffclassification.repository.{CaseMongoRepository, EventMongoRepository, SequenceMongoRepository}
import uk.gov.hmrc.bindingtariffclassification.scheduler.ScheduledJobs
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.test.HttpClientV2Support
import uk.gov.hmrc.mongo.lock.{LockRepository, MongoLockRepository}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import util.TestMetrics

import scala.concurrent.Await.result
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait IntegrationSpecBase
    extends AnyWordSpecLike
    with WiremockHelper
    with Matchers
    with MockitoSugar
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with Eventually
    with HttpClientV2Support {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  trait MockHasMetrics {
    self: HasMetrics =>
    val timer: Timer.Context                = mock[Timer.Context]
    val metrics: Metrics                    = mock[Metrics]
    override val localMetrics: LocalMetrics = mock[LocalMetrics]
    when(localMetrics.startTimer(anyString())) thenReturn timer
  }

  object FakeHasMetrics extends HasMetrics with MockHasMetrics

  override implicit lazy val app: Application =
    new GuiceApplicationBuilder()
      .in(Environment.simple(mode = Mode.Dev))
      .configure(config)
      .overrides(bind[Metrics].toInstance(new TestMetrics))
      .overrides(bind[HasMetrics].toInstance(FakeHasMetrics))
      .overrides(bind[HttpClientV2].toInstance(httpClientV2))
      .build()

  val mockHost: String = WiremockHelper.wiremockHost
  val mockPort: String = WiremockHelper.wiremockPort.toString
  val mockUrl          = s"http://$mockHost:$mockPort"

  def config: Map[String, Any] = Map(
    "application.router"                -> "testOnlyDoNotUseInAppConf.Routes",
    "microservice.services.auth.host"   -> mockHost,
    "microservice.services.auth.port"   -> mockPort,
    "microservice.services.des.host"    -> mockHost,
    "microservice.services.des.port"    -> mockPort,
    "microservice.services.nrs.host"    -> mockHost,
    "microservice.services.nrs.port"    -> mockPort,
    "microservice.services.nrs.enabled" -> true,
    "microservice.services.nrs.apikey"  -> "test",
    "internalServiceHostPatterns"       -> Nil
  )

  protected val timeout: FiniteDuration = 5.seconds

  private lazy val caseStore: CaseMongoRepository           = app.injector.instanceOf[CaseMongoRepository]
  private lazy val eventStore: EventMongoRepository         = app.injector.instanceOf[EventMongoRepository]
  private lazy val sequenceStore: SequenceMongoRepository   = app.injector.instanceOf[SequenceMongoRepository]
  private lazy val mongoLockRepository: MongoLockRepository = app.injector.instanceOf[MongoLockRepository]
  private lazy val scheduledJobStores: Iterable[LockRepository] =
    app.injector.instanceOf[ScheduledJobs].jobs.map(_.lockRepository)

  def dropStores(): Iterable[Unit] = {
    result(caseStore.collection.withWriteConcern(WriteConcern.JOURNALED).drop().toFuture(), timeout)
    result(eventStore.collection.withWriteConcern(WriteConcern.JOURNALED).drop().toFuture(), timeout)
    result(sequenceStore.collection.withWriteConcern(WriteConcern.JOURNALED).drop().toFuture(), timeout)
    result(mongoLockRepository.collection.withWriteConcern(WriteConcern.JOURNALED).drop().toFuture(), timeout)
    result(
      Future.traverse(scheduledJobStores)(_.asInstanceOf[MongoLockRepository].collection.drop().toFuture()),
      timeout
    )
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

  protected def caseStoreSize: Long =
    result(caseStore.collection.countDocuments().toFuture(), timeout)

  protected def eventStoreSize: Long =
    result(eventStore.collection.countDocuments().toFuture(), timeout)

  protected def getCase(ref: String): Option[Case] =
    // for simplicity decryption is not tested here (because disabled in application.conf)
    result(caseStore.getByReference(ref), timeout)

  override def beforeEach(): Unit = {
    dropStores()
    resetWiremock()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    startWiremock()
  }

  override def afterAll(): Unit = {
    stopWiremock()
    super.afterAll()
  }

}
