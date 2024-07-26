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

package uk.gov.hmrc.bindingtariffclassification.component.controllers

import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.bindingtariffclassification.component.utils.{AuthStub, IntegrationSpecBase}
import uk.gov.hmrc.bindingtariffclassification.model.bta.{BtaApplications, BtaCard, BtaRulings}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HttpResponse, StringContextOps}
import util.CaseData

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class BtaCardControllerISpec extends IntegrationSpecBase {

  protected val serviceUrl = s"http://localhost:$port"
  protected val eori       = "GB123"

  val httpClient: HttpClientV2 = httpClientV2

  "GET /bta-card" when {

    "There are applications and rulings in the collection " must {

      "return 200 and the correct the BTA Card counts" in {

        AuthStub.authorised()
        dropStores()
        storeCases(
          CaseData.createBtaCardData(
            eori = eori,
            totalApplications = 2,
            actionableApplications = 1,
            totalRulings = 6,
            expiringRulings = 3
          ): _*
        )

        val responseFuture =
          httpClient
            .get(url"$serviceUrl/bta-card")
            .setHeader("Authorization" -> "Auth")
            .execute[HttpResponse]

        val result = Await.result(responseFuture, Duration(1000L, "ms"))

        result.status                                    shouldBe OK
        Json.parse(result.body).as[BtaCard].eori         shouldBe eori
        Json.parse(result.body).as[BtaCard].applications shouldBe Some(BtaApplications(2, 1))
        Json.parse(result.body).as[BtaCard].rulings      shouldBe Some(BtaRulings(6, 3))
      }
    }

    "Only Rulings are present" must {

      "return 200 and the correct the BTA Card counts" in {

        AuthStub.authorised()
        dropStores()
        storeCases(
          CaseData.createBtaCardData(
            eori = eori,
            totalApplications = 0,
            actionableApplications = 0,
            totalRulings = 2,
            expiringRulings = 2
          ): _*
        )

        val responseFuture =
          httpClient
            .get(url"$serviceUrl/bta-card")
            .setHeader("Authorization" -> "Auth")
            .execute[HttpResponse]

        val result = Await.result(responseFuture, Duration(1000L, "ms"))

        result.status shouldEqual OK

        Json.parse(result.body).as[BtaCard].eori         shouldBe eori
        Json.parse(result.body).as[BtaCard].applications shouldBe None
        Json.parse(result.body).as[BtaCard].rulings      shouldBe Some(BtaRulings(2, 2))
      }
    }

    "Only Applications are present" must {

      "return 200 and the correct the BTA Card counts" in {

        AuthStub.authorised()
        dropStores()
        storeCases(CaseData.createBtaCardData(eori, 10, 9, 0, 0): _*)

        val responseFuture =
          httpClient
            .get(url"$serviceUrl/bta-card")
            .setHeader("Authorization" -> "Auth")
            .execute[HttpResponse]

        val result = Await.result(responseFuture, Duration(1000L, "ms"))

        result.status shouldEqual OK

        Json.parse(result.body).as[BtaCard].eori         shouldBe eori
        Json.parse(result.body).as[BtaCard].applications shouldBe Some(BtaApplications(10, 9))
        Json.parse(result.body).as[BtaCard].rulings      shouldBe None
      }
    }

    "No cases (applications or rulings) are present" must {

      "return 200 and the BTA Card count is None" in {

        AuthStub.authorised()
        dropStores()

        val responseFuture =
          httpClient
            .get(url"$serviceUrl/bta-card")
            .setHeader("Authorization" -> "Auth")
            .execute[HttpResponse]

        val result = Await.result(responseFuture, Duration(1000L, "ms"))

        result.status shouldEqual OK

        Json.parse(result.body).as[BtaCard].eori         shouldBe eori
        Json.parse(result.body).as[BtaCard].applications shouldBe None
        Json.parse(result.body).as[BtaCard].rulings      shouldBe None
      }
    }

    "the user does not provide an Authorization Header" must {

      "return 403 Forbidden and denied access" in {

        dropStores()

        val responseFuture =
          httpClient
            .get(url"$serviceUrl/bta-card")
            .setHeader("Authorization" -> "Auth")
            .execute[HttpResponse]

        val result = Await.result(responseFuture, Duration(1000L, "ms"))

        result.status shouldEqual FORBIDDEN
      }
    }

    "the user is unauthorised" must {

      "return 403 Forbidden and denied access" in {

        AuthStub.unauthorised()

        dropStores()

        val responseFuture =
          httpClient
            .get(url"$serviceUrl/bta-card")
            .setHeader("Authorization" -> "Auth")
            .execute[HttpResponse]

        val result = Await.result(awaitable = responseFuture, atMost = Duration(1000L, "ms"))
        result.status shouldEqual FORBIDDEN
      }
    }

  }
}
