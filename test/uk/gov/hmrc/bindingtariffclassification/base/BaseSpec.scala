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

package uk.gov.hmrc.bindingtariffclassification.base

import com.codahale.metrics.Timer
import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.{clearAllCaches, clearInvocations, reset, when}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{BodyParsers, MessagesControllerComponents}
import play.api.{Application, Configuration}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.connector.ResourceFiles
import uk.gov.hmrc.bindingtariffclassification.metrics.HasMetrics
import uk.gov.hmrc.bindingtariffclassification.migrations.MigrationJob
import uk.gov.hmrc.bindingtariffclassification.repository.MigrationLockMongoRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.test.HttpClientV2Support
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

abstract class BaseSpec
    extends AnyWordSpecLike
    with GuiceOneAppPerSuite
    with MockitoSugar
    with ResourceFiles
    with Matchers
    with ScalaFutures
    with MockHttpClient
    with HttpClientV2Support
    with BeforeAndAfterEach
    with BeforeAndAfterAll {

  trait MockHasMetrics {
    self: HasMetrics =>
    val timer: Timer.Context                = mock[Timer.Context]
    val metrics: Metrics                    = mock[Metrics]
    override val localMetrics: LocalMetrics = mock[LocalMetrics]
    when(localMetrics.startTimer(anyString())) thenReturn timer
  }

  object FakeHasMetrics extends HasMetrics with MockHasMetrics

  implicit lazy val ec: ExecutionContext = fakeApplication.injector.instanceOf[ExecutionContext]

  val mockMigrationJob: MigrationJob                                 = mock[MigrationJob]
  val mockMigrationLockMongoRepository: MigrationLockMongoRepository = mock[MigrationLockMongoRepository]

  val mockMetrics: Metrics               = mock[Metrics]
  val mockHasMetrics: HasMetrics         = mock[HasMetrics]
  val mockMongoComponent: MongoComponent = mock[MongoComponent]

  override lazy val fakeApplication: Application =
    GuiceApplicationBuilder()
      .configure(
        "metrics.jvm"                             -> false,
        "metrics.enabled"                         -> false,
        "scheduler.active-days-elapsed.enabled"   -> false,
        "scheduler.referred-days-elapsed.enabled" -> false,
        "scheduler.filestore-cleanup.enabled"     -> false
      )
      .overrides(bind[Metrics].toInstance(mockMetrics))
      .overrides(bind[HasMetrics].toInstance(FakeHasMetrics))
      .overrides(bind[HttpClientV2].toInstance(httpClientV2))
      .build()

  implicit val hc: HeaderCarrier              = HeaderCarrier()
  implicit val defaultTimeout: FiniteDuration = 5.seconds

  def await[A](future: Future[A])(implicit timeout: Duration): A =
    Await.result(future, timeout)

  val fakeMongoComponent = fakeApplication.injector.instanceOf[MongoComponent]
  val fakeAppConfig      = fakeApplication.injector.instanceOf[AppConfig]
  val fakeHttpClientV2   = fakeApplication.injector.instanceOf[HttpClientV2]

  lazy val parser: BodyParsers.Default       = fakeApplication.injector.instanceOf[BodyParsers.Default]
  lazy val mcc: MessagesControllerComponents = fakeApplication.injector.instanceOf[MessagesControllerComponents]
  implicit val mat: Materializer             = fakeApplication.materializer

  implicit def liftFuture[A](v: A): Future[A] = Future.successful(v)

  lazy val realConfig: Configuration     = fakeApplication.injector.instanceOf[Configuration]
  lazy val serviceConfig: ServicesConfig = fakeApplication.injector.instanceOf[ServicesConfig]

}
