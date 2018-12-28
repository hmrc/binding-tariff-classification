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

package uk.gov.hmrc.bindingtariffclassification.connector

import java.time.LocalDate

import akka.actor.ActorSystem
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get}
import org.mockito.BDDMockito.given
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.Environment
import play.api.libs.ws.WSClient
import uk.gov.hmrc.bindingtariffclassifcation.connector.{ResourceFiles, WiremockTestServer}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.audit.DefaultAuditConnector
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class BankHolidaysConnectorTest extends UnitSpec with WiremockTestServer with MockitoSugar with WithFakeApplication with BeforeAndAfterEach with ResourceFiles {

  private val config = mock[AppConfig]
  private implicit val headers: HeaderCarrier = HeaderCarrier()

  private val wsClient: WSClient = fakeApplication.injector.instanceOf[WSClient]
  private val auditConnector = new DefaultAuditConnector(fakeApplication.configuration, fakeApplication.injector.instanceOf[Environment])
  private val hmrcWsClient = new DefaultHttpClient(fakeApplication.configuration, auditConnector, wsClient, ActorSystem("test"))

  private val connector = new BankHolidaysConnector(config, hmrcWsClient)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(config.bankHolidaysUrl).willReturn(wireMockUrl)
  }

  "Connector" should {
    "GET" in {
      stubFor(
        get("/bank-holidays.json")
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
  }
}
