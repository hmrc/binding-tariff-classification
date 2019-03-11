/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time.LocalDate

import akka.actor.ActorSystem
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get}
import org.mockito.BDDMockito.given
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.Environment
import play.api.http.Status
import play.api.libs.ws.WSClient
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.http.ProxyHttpClient
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.audit.DefaultAuditConnector
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class BankHolidaysConnectorTest extends UnitSpec with WiremockTestServer
  with MockitoSugar with WithFakeApplication with BeforeAndAfterEach with ResourceFiles {

  private val config = mock[AppConfig]

  private implicit val headers: HeaderCarrier = HeaderCarrier()
  private implicit val actorSystem = ActorSystem("test")

  private val wsClient: WSClient = fakeApplication.injector.instanceOf[WSClient]
  private val environment = fakeApplication.injector.instanceOf[Environment]
  private val auditConnector = new DefaultAuditConnector(fakeApplication.configuration, environment)
  private val hmrcProxyHttpClient = new ProxyHttpClient(fakeApplication.configuration, auditConnector, wsClient)

  private val connector = new BankHolidaysConnector(config, hmrcProxyHttpClient)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(config.bankHolidaysUrl).willReturn(wireMockUrl)
  }

  "Connector" should {
    "GET" in {
      stubFor(
        get("/bank-holidays")
          .willReturn(
            aResponse()
              .withBody(fromFile("bank-holidays.json"))
          )
      )

      await(connector.get()) shouldBe Seq(
        LocalDate.of(2012,1,2),
        LocalDate.of(2012,4,6)
      )
    }

    "Fallback to resources on 4xx" in {
      stubFor(
        get("/bank-holidays")
          .willReturn(
            aResponse().withStatus(Status.NOT_FOUND)
          )
      )

      await(connector.get()).size shouldBe 73
    }

    "Fallback to resources on 5xx" in {
      stubFor(
        get("/bank-holidays")
          .willReturn(
            aResponse().withStatus(Status.BAD_GATEWAY)
          )
      )

      await(connector.get()).size shouldBe 73
    }
  }

}
