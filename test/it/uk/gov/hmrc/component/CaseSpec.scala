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
import uk.gov.hmrc.bindingtariffclassification.model.{Case, CaseStatus}
import uk.gov.hmrc.bindingtariffclassification.model.JsonFormatters._
import uk.gov.hmrc.bindingtariffclassification.todelete.CaseData

class CaseSpec extends BaseFeatureSpec {

  override lazy val port = 14681

  private val c1 = CaseData.createCase(CaseData.createBTIApplication)
  private val c1_updated = c1.copy(status = CaseStatus.CANCELLED)
  private val c2 = CaseData.createCase(CaseData.createBTIApplication)

  private val json1 = Json.toJson(c1)
  private val json1_updated = Json.toJson(c1_updated)


  feature("Create Case") {

    scenario("Create a new case") {

      When("I create a new case")
      val result = Http(s"$serviceUrl/cases")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .timeout(5000, 10000)
        .postData(json1.toString()).asString

      Then("The case is returned in the JSON response")
      Json.parse(result.body) shouldBe json1

      And("The response code should be created")
      result.code shouldEqual CREATED
    }

    scenario("Create an existing case") {

      Given("There is a case in the database")
      store(c1)

      When("I create a case that already exist")
      val result = Http(s"$serviceUrl/cases")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .timeout(5000, 10000)
        .postData(json1.toString()).asString

      // TODO This should not return an internal server error. Instead it should return a 422
      // requires a code change to the application but is not currently blocking us so the test has been left
      // testing for a 500 internal server error.
      Then("The response code should be internal server error")
      result.code shouldEqual INTERNAL_SERVER_ERROR
    }

  }


  feature("Update Case") {

    scenario("Update an non-existing case") {

      When("I update a non-existing case")
      val result = Http(s"$serviceUrl/cases/${c1.reference}")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .timeout(5000, 10000)
        .put(json1.toString()).asString

      Then("The response code should be NOT FOUND")
      result.code shouldEqual NOT_FOUND
    }

    scenario("Update an existing case") {

      Given("There is a case in the database")
      store(c1)

      When("I update an existing case")
      val result = Http(s"$serviceUrl/cases/${c1.reference}")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .timeout(5000, 10000)
        .put(json1_updated.toString()).asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The case is returned in the JSON response")
      Json.parse(result.body) shouldBe json1_updated
    }

  }


  feature("Get Case") {

    scenario("Get existing case") {

      Given("There is a case in the database")
      store(c1)

      When("I get a case")
      val result = Http(s"$serviceUrl/cases/${c1.reference}")
        .timeout(5000, 10000).asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The case is returned in the JSON response")
      Json.parse(result.body) shouldBe json1
    }

    scenario("Get a non-existing case") {

      When("I get a case")
      val result = Http(s"$serviceUrl/cases/${c1.reference}")
        .timeout(5000, 10000).asString

      Then("The response code should be NOT FOUND")
      result.code shouldEqual NOT_FOUND
    }

  }


  feature("Get All Cases") {

    // TODO: test all possible combinations

    scenario("Get cases") {

      Given("There are few cases in the database")
      store(c1)
      store(c2)

      When("I get all cases")
      val result = Http(s"$serviceUrl/cases")
        .timeout(5000, 10000).asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq(c1, c2))
    }

    scenario("Get no cases") {

      Given("There are no cases in the database")

      When("I get all cases")
      val result = Http(s"$serviceUrl/cases")
        .timeout(5000, 10000).asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("No cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq.empty[Case])
    }

  }

}
