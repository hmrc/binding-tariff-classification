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

package uk.gov.hmrc.bindingtariffclassification.component.utils

import org.apache.pekko.actor.ActorSystem
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatestplus.play.BaseOneServerPerSuite

object WiremockHelper {
  val wiremockPort = 11111
  val wiremockHost = "localhost"
  val wiremockURL  = s"http://$wiremockHost:$wiremockPort"
}

trait WiremockHelper {
  self: BaseOneServerPerSuite =>

  import WiremockHelper._
  lazy val wmConfig: WireMockConfiguration = wireMockConfig().port(wiremockPort)
  val wireMockServer                       = new WireMockServer(wmConfig)

  implicit val system: ActorSystem = ActorSystem("my-system")

  def startWiremock(): Unit = {
    WireMock.configureFor(wiremockHost, wiremockPort)
    wireMockServer.start()
  }

  def stopWiremock(): Unit = wireMockServer.stop()

  def resetWiremock(): Unit = WireMock.reset()

  def verifyCall(url: String): Unit = WireMock.verify(postRequestedFor(urlPathEqualTo(url)))

  def verifyCalls(url: String, numberOfCalls: Int): Unit =
    WireMock.verify(numberOfCalls, postRequestedFor(urlPathEqualTo(url)))

  def verifyNoCall(url: String): Unit = WireMock.verify(0, postRequestedFor(urlPathEqualTo(url)))

  def stubGet(url: String, status: Integer, body: String = ""): StubMapping =
    stubFor(
      get(urlMatching(url))
        .willReturn(
          aResponse().withStatus(status).withBody(body)
        )
    )

  def stubPost(url: String, status: Integer, responseBody: String = ""): StubMapping =
    stubFor(
      post(urlMatching(url))
        .willReturn(
          aResponse().withStatus(status).withBody(responseBody)
        )
    )

  def stubDelete(url: String, status: Integer): StubMapping =
    stubFor(
      delete(urlMatching(url))
        .willReturn(
          aResponse()
            .withStatus(status)
        )
    )

  def stubPut(url: String, status: Integer, responseBody: String = ""): StubMapping =
    stubFor(
      put(urlMatching(url))
        .willReturn(
          aResponse().withStatus(status).withBody(responseBody)
        )
    )
}
