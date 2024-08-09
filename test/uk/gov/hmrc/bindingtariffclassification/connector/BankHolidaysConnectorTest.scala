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

package uk.gov.hmrc.bindingtariffclassification.connector

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.mockito.BDDMockito.given
import play.api.http.Status.{BAD_GATEWAY, NOT_FOUND}
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.HttpClientV2Support
import util.TestMetrics

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

class BankHolidaysConnectorTest extends BaseSpec with WiremockTestServer with HttpClientV2Support {

  //updated to test with some sort of proxy

  private val config = mock[AppConfig]

  private implicit val headers: HeaderCarrier = HeaderCarrier()

  private val connector = new BankHolidaysConnector(config, httpClientV2, new TestMetrics)

  private val proxyPort: Int              = 20002
  private val proxyServer: WireMockServer = new WireMockServer(options().port(proxyPort).enableBrowserProxying(true))
  private val proxyUrl: String            = s"http://$host:$proxyPort"

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    proxyServer.start()
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    proxyServer.stop()
  }

  private class Test(response: ResponseDefinitionBuilder) {
    given(config.bankHolidaysUrl).willReturn(s"$proxyUrl/bank-holidays.json")

    proxyServer.stubFor(
      get(urlEqualTo("/bank-holidays.json"))
        .willReturn(
          aResponse()
            .proxiedFrom(wireMockUrl)
        )
    )

    stubFor(get(urlEqualTo("/bank-holidays.json")).willReturn(response))
  }

  "BankHolidaysConnector" should {
    "GET bank holidays" in new Test(aResponse().withBody(fromFile("bank-holidays.json"))) {
      await(connector.get()) shouldBe
        Set(
          LocalDate.of(2012, 1, 2),
          LocalDate.of(2012, 4, 6)
        )
    }

    "Fallback to resources on 4xx" in new Test(aResponse().withStatus(NOT_FOUND)) {
      await(connector.get()).size shouldBe 67
    }

    "Fallback to resources on 5xx" in new Test(aResponse().withStatus(BAD_GATEWAY)) {
      await(connector.get()).size shouldBe 67
    }
  }
}
