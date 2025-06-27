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

package uk.gov.hmrc.bindingtariffclassification.base

import org.apache.pekko.stream.Materializer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{BodyParsers, MessagesControllerComponents}
import play.api.{Application, Configuration}
import uk.gov.hmrc.bindingtariffclassification.connector.ResourceFiles
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import util.TestMetrics

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}

abstract class BaseSpec
    extends AnyWordSpecLike
    with GuiceOneAppPerSuite
    with MockitoSugar
    with ResourceFiles
    with Matchers
    with ScalaFutures {

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .configure(
      "metrics.jvm"                             -> false,
      "metrics.enabled"                         -> false,
      "scheduler.active-days-elapsed.enabled"   -> false,
      "scheduler.referred-days-elapsed.enabled" -> false,
      "scheduler.filestore-cleanup.enabled"     -> false,
      "mongodb.replace-indexes"                 -> true
    )
    .overrides(bind[Metrics].toInstance(new TestMetrics))
    .build()

  given mat: Materializer                     = fakeApplication().materializer
  given hc: HeaderCarrier                     = HeaderCarrier()
  implicit def liftFuture[A](v: A): Future[A] = Future.successful(v)

  lazy val realConfig: Configuration         = fakeApplication().injector.instanceOf[Configuration]
  lazy val serviceConfig: ServicesConfig     = fakeApplication().injector.instanceOf[ServicesConfig]
  lazy val parser: BodyParsers.Default       = fakeApplication().injector.instanceOf[BodyParsers.Default]
  lazy val mcc: MessagesControllerComponents = fakeApplication().injector.instanceOf[MessagesControllerComponents]
  given defaultTimeout: FiniteDuration       = 5.seconds
  def await[A](future: Future[A])(implicit timeout: Duration): A = Await.result(future, timeout)

}
