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
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.ContentTypes.JSON
import play.api.http.Status.{CREATED, INTERNAL_SERVER_ERROR, NOT_FOUND, OK}
import play.api.libs.json.Json
import uk.gov.hmrc.bindingtariffclassification.model.JsonFormatters._
import uk.gov.hmrc.bindingtariffclassification.todelete.CaseData

class CaseSpec extends BaseFeatureSpec {

  override lazy val port = 14681

  private val c = CaseData.createCase(CaseData.createBTIApplication)
  private val expectedCaseBody = Json.toJson(c)

  feature("Create Case") {

    scenario("Create a new case") {

      When("I create a new case")
      val result = Http(s"$serviceUrl/cases")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .timeout(5000, 10000)
        .postData(expectedCaseBody.toString()).asString

      Then("The case body is returned")
      Json.parse(result.body) shouldBe expectedCaseBody

      And("The response code should be created")
      result.code shouldEqual CREATED
    }

    scenario("Create an existing reference case") {

      Given("There is a case in the database")
      store(c)

      When("I create a case that already exist")
      val result = Http(s"$serviceUrl/cases")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .timeout(5000, 10000)
        .postData(expectedCaseBody.toString()).asString

      // TODO This should not return an internal server error. Instead it should return a 422
      // requires a code change to the application but is not currently blocking us so the test has been left
      // testing for a 500 internal server error.
      And("The response code should be internal server error")
      result.code shouldEqual INTERNAL_SERVER_ERROR
    }

  }


  feature("Get case") {

    scenario("Get existing case") {

      Given("There is a case in the database")
      store(c)

      When("I get a case")
      val result = Http(s"$serviceUrl/cases/${c.reference}")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .timeout(5000, 10000).asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      Then("The case body is returned")
      Json.parse(result.body) shouldBe expectedCaseBody
    }

    scenario("Get a non-existing case") {

      When("I get a case")
      val result = Http(s"$serviceUrl/cases/${c.reference}")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .timeout(5000, 10000).asString

      Then("The response code should be NOT FOUND")
      result.code shouldEqual NOT_FOUND
    }

  }

  // TODO: add tests for other routes
}
