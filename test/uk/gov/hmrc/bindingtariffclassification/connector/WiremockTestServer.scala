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

package uk.gov.hmrc.bindingtariffclassification.connector

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.{MappingBuilder, WireMock}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

trait WiremockTestServer extends AnyWordSpecLike with BeforeAndAfterEach {

  protected val host         = "localhost"
  protected val wirePort     = 20001
  private val wireMockServer = new WireMockServer(wirePort)

  lazy val wireMockUrl: String = s"http://$host:$wirePort"

  protected def stubFor(mappingBuilder: MappingBuilder): StubMapping =
    wireMockServer.stubFor(mappingBuilder)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    wireMockServer.start()
    WireMock.configureFor(host, wirePort)
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    wireMockServer.stop()
  }

}
