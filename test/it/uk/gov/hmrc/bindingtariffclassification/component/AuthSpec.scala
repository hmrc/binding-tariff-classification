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

package uk.gov.hmrc.bindingtariffclassification.component

import java.util.UUID

import play.api.http.Status._
import play.api.http.{HttpVerbs, Status}
import scalaj.http.Http
import util.CaseData.createCase

class AuthSpec extends BaseFeatureSpec {

  override lazy val port = 14682
  protected val serviceUrl = s"http://localhost:$port"

  private val caseRef = UUID.randomUUID().toString
  private val c1 = createCase(r = caseRef)


  feature("Authentication") {

    scenario("Auth header present with correct value") {

      When("I call an endpoint with the correct auth token")
      val result = Http(s"$serviceUrl/cases")
        .header(apiTokenKey, appConfig.authorization)
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK
    }

    scenario("Auth header present with incorrect value") {

      When("I call an endpoint with an incorrect auth token")
      val result = Http(s"$serviceUrl/cases")
        .header(apiTokenKey, "WRONG_TOKEN")
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should be 403")
      result.code shouldEqual Status.FORBIDDEN
    }

    scenario("Auth header not present") {

      When("I call an endpoint with the no auth token")
      val result = Http(s"$serviceUrl/cases")
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should be 403")
      result.code shouldEqual Status.FORBIDDEN
    }
  }
}
