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

package uk.gov.hmrc.bindingtariffclassification.component.utils

import org.scalatest._
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Environment, Mode}

trait IntegrationSpecBase
    extends TestSuite
    with WiremockHelper
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with Eventually {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure(config)
    .build
  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort.toString
  val mockUrl  = s"http://$mockHost:$mockPort"

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

  override def beforeEach(): Unit =
    resetWiremock()

  override def beforeAll(): Unit = {
    super.beforeAll()
    startWiremock()
  }

  override def afterAll(): Unit = {
    stopWiremock()
    super.afterAll()
  }

}
