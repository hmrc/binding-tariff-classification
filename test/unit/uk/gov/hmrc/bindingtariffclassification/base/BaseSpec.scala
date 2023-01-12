/*
 * Copyright 2023 HM Revenue & Customs
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

import akka.stream.Materializer
import com.kenshoo.play.metrics.Metrics
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
import util.TestMetrics

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.concurrent.{Await, Future}
import scala.language.implicitConversions

abstract class BaseSpec
    extends AnyWordSpecLike
    with GuiceOneAppPerSuite
    with MockitoSugar
    with ResourceFiles
    with Matchers
    with ScalaFutures {

  override lazy val fakeApplication: Application = GuiceApplicationBuilder()
    .configure(
      "metrics.jvm"                             -> false,
      "metrics.enabled"                         -> false,
      "scheduler.active-days-elapsed.enabled"   -> false,
      "scheduler.referred-days-elapsed.enabled" -> false,
      "scheduler.filestore-cleanup.enabled"     -> false
    )
    .overrides(bind[Metrics].toInstance(new TestMetrics))
    .build()

  implicit val mat: Materializer              = fakeApplication.materializer
  implicit val hc: HeaderCarrier              = HeaderCarrier()
  implicit def liftFuture[A](v: A): Future[A] = Future.successful(v)

  lazy val realConfig: Configuration                             = fakeApplication.injector.instanceOf[Configuration]
  lazy val serviceConfig: ServicesConfig                         = fakeApplication.injector.instanceOf[ServicesConfig]
  lazy val parser: BodyParsers.Default                           = fakeApplication.injector.instanceOf[BodyParsers.Default]
  lazy val mcc: MessagesControllerComponents                     = fakeApplication.injector.instanceOf[MessagesControllerComponents]
  implicit val defaultTimeout: FiniteDuration                    = 5.seconds
  def await[A](future: Future[A])(implicit timeout: Duration): A = Await.result(future, timeout)

}
