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

package uk.gov.hmrc.bindingtariffclassification.component

import play.api.http.Status._
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import uk.gov.hmrc.bindingtariffclassification.component.utils.IntegrationSpecBase

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class AuthSpec extends BaseFeatureSpec with IntegrationSpecBase {

  protected val serviceUrl = s"http://localhost:$port"

  val httpClient: StandaloneAhcWSClient = StandaloneAhcWSClient()

  Feature("Authentication") {

    Scenario("Auth header present with correct value") {

      When("I call an endpoint with the correct auth token")
      val responseFuture =
        httpClient.url(s"$serviceUrl/cases").withHttpHeaders(apiTokenKey -> appConfig.authorization).get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 200")
      result.status shouldEqual OK
    }

    Scenario("Auth header present with incorrect value") {

      When("I call an endpoint with an incorrect auth token")
      val responseFuture = httpClient.url(s"$serviceUrl/cases").withHttpHeaders(apiTokenKey -> "WRONG_TOKEN").get()
      val result         = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 403")
      result.status shouldEqual FORBIDDEN
      result.body shouldBe "Missing or invalid 'X-Api-Token'"
    }

    Scenario("Auth header not present") {

      When("I call an endpoint with the no auth token")
      val responseFuture = httpClient.url(s"$serviceUrl/cases").get()
      val result         = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 403")
      result.status shouldEqual FORBIDDEN
      result.body shouldBe "Missing or invalid 'X-Api-Token'"
    }

    Scenario("Calls to the health endpoint do not require auth token") {
      val responseFuture = httpClient.url(s"$serviceUrl/ping/ping").get()
      val result         = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 200")
      result.status shouldEqual OK
    }
  }
}
