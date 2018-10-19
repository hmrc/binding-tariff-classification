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

package it.uk.gov.hmrc.component

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import it.uk.gov.hmrc.stubs._
import org.scalatest._
import org.scalatestplus.play.OneServerPerSuite
import uk.gov.hmrc.bindingtariffclassification.model.Case
import uk.gov.hmrc.bindingtariffclassification.repository.CaseMongoRepository

import scala.concurrent.Await
import scala.concurrent.Await.result
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

case class MockHost(port: Int) {
  val server = new WireMockServer(WireMockConfiguration.wireMockConfig().port(port))
  val mock = new WireMock("localhost", port)
  val url = s"http://localhost:$port"

  val userAgent = "binding-tariff-classification"
}

abstract class BaseFeatureSpec extends FeatureSpec
  with Matchers with GivenWhenThen with OneServerPerSuite
  with BeforeAndAfterEach with BeforeAndAfterAll {

  override lazy val port = 14680
  val serviceUrl = s"http://localhost:$port"

  val timeout = 2.seconds
  val mocks = Seq(CaseStub)

  override protected def beforeEach(): Unit = {
    mocks.foreach(m => if (!m.server.isRunning) m.server.start())

    result(mongoRepository.drop, timeout)
    result(mongoRepository.ensureIndexes, timeout)
  }

  def mongoRepository = app.injector.instanceOf[CaseMongoRepository]

  def store(caseModel: Case) = {
    Await.result(mongoRepository.insert(caseModel), timeout)
  }

  override protected def afterEach(): Unit = {
    mocks.foreach(_.mock.resetMappings())
  }

  override protected def afterAll(): Unit = {
    mocks.foreach(_.server.stop())
    result(mongoRepository.drop, timeout)
    result(mongoRepository.ensureIndexes, timeout)
  }
}
