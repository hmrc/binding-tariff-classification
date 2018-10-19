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

import scalaj.http.Http
import play.api.libs.json.Json
import uk.gov.hmrc.bindingtariffclassification.todelete.CaseData

class CaseSpec extends BaseFeatureSpec {

  val userId = "userId"
  val accessToken = "access_token"
  val authToken = "auth_token"
  val caseModel = CaseData.createCase(CaseData.createBTIApplication)


  import uk.gov.hmrc.bindingtariffclassification.model.JsonFormatters._

  feature("Create Case") {

    val expectedCaseBody = Json.toJson(caseModel)

    scenario("Create a new case") {

      When("I create a new case")
      val result = Http(s"$serviceUrl/cases")
        .headers(Seq("Content-Type" -> "application/json")).timeout(5000, 10000)
        .postData(expectedCaseBody.toString()).asString

      Then("The case body is returned")
      Json.parse(result.body) shouldBe expectedCaseBody

      And("The response code should be created")
      result.code shouldEqual 201
    }

    scenario("Create an existing reference case") {

      Given("There is a case in the database")
      store(caseModel)

      When("I create a case that already exist")
      val result = Http(s"$serviceUrl/cases")
        .headers(Seq("Content-Type" -> "application/json")).timeout(5000, 10000)
        .postData(expectedCaseBody.toString()).asString

      // TODO This should not return an internal server error. Instead it should return a 422
      // requires a code change to the application but is not currently blocking us so the test has been left
      // testing for a 500 internal server error.
      And("The response code should be internal server error")
      result.code shouldEqual 500
    }

  }

}
