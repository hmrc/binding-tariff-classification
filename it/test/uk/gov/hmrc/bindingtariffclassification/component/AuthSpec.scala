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

import play.api.http.Status.{FORBIDDEN, OK}
import uk.gov.hmrc.bindingtariffclassification.component.utils.IntegrationSpecBase
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HttpResponse, StringContextOps}

class AuthSpec extends IntegrationSpecBase {

  protected val serviceUrl = s"http://localhost:$port"

  val httpClient: HttpClientV2 = httpClientV2

  "GET /cases" when {

    "Auth header present with correct value" must {

      "return 200 OK" in {

        val responseFuture =
          httpClient
            .get(url"$serviceUrl/cases")
            .setHeader(apiTokenKey -> appConfig.authorization)
            .execute[HttpResponse]

        val result = await(responseFuture)

        result.status shouldBe OK
      }
    }

    "Auth header present with wrong value" must {

      "return 403 FORBIDDEN" in {

        val responseFuture =
          httpClient
            .get(url"$serviceUrl/cases")
            .setHeader(apiTokenKey -> "WRONG_TOKEN")
            .execute[HttpResponse]

        val result = await(responseFuture)

        result.status shouldBe FORBIDDEN
        result.body   shouldBe "Missing or invalid 'X-Api-Token'"
      }
    }

    "Auth header not present" must {

      "return 403 FORBIDDEN" in {

        val responseFuture =
          httpClient
            .get(url"$serviceUrl/cases")
            .execute[HttpResponse]

        val result = await(responseFuture)

        result.status shouldBe FORBIDDEN
        result.body   shouldBe "Missing or invalid 'X-Api-Token'"
      }
    }

    "Calls to the health endpoint do not require auth token" must {

      "return 200 OK" in {

        val responseFuture =
          httpClient
            .get(url"$serviceUrl/ping/ping")
            .execute[HttpResponse]

        val result = await(responseFuture)

        result.status shouldBe OK
      }
    }
  }
}
