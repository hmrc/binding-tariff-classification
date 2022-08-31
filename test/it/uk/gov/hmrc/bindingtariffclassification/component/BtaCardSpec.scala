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

import play.api.http.HttpVerbs
import play.api.http.Status._
import play.api.libs.json.Json
import scalaj.http.Http
import uk.gov.hmrc.bindingtariffclassification.component.utils.{AuthStub, IntegrationSpecBase}
import uk.gov.hmrc.bindingtariffclassification.model.bta.{BtaApplications, BtaCard, BtaRulings}
import util.CaseData

// scalastyle:off magic.number
class BtaCardSpec extends BaseFeatureSpec with IntegrationSpecBase {

  protected val serviceUrl = s"http://localhost:$port"
  protected val eori       = "GB123"

  Feature("Get BTA Card") {

    Scenario("BTA Card counts where both Applications and Rulings are present") {

      Given("There are applications and rulings in the collection")

      AuthStub.authorised()
      dropStores()
      storeCases(CaseData.createBtaCardData(eori, 2, 1, 6, 3): _*)

      When("I call the BTA Card endpoint for the counts")
      val result = Http(s"$serviceUrl/bta-card")
        .header("Authorization", "Auth")
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The EORI should be present")
      Json.parse(result.body).as[BtaCard].eori shouldBe eori

      And("The Applications counts should be correct")
      Json.parse(result.body).as[BtaCard].applications shouldBe Some(BtaApplications(2, 1))

      And("The Rulings counts should be correct")
      Json.parse(result.body).as[BtaCard].rulings shouldBe Some(BtaRulings(6, 3))
    }

    Scenario("BTA Card counts where only Rulings are present") {

      Given("There are rulings in the collection")

      AuthStub.authorised()
      dropStores()
      storeCases(CaseData.createBtaCardData(eori, 0, 0, 2, 2): _*)

      When("I call the BTA Card endpoint for the counts")
      val result = Http(s"$serviceUrl/bta-card")
        .header("Authorization", "Auth")
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The EORI should be present")
      Json.parse(result.body).as[BtaCard].eori shouldBe eori

      And("The Applications counts should be None")
      Json.parse(result.body).as[BtaCard].applications shouldBe None

      And("The Rulings counts should be correct")
      Json.parse(result.body).as[BtaCard].rulings shouldBe Some(BtaRulings(2, 2))
    }

    Scenario("BTA Card counts where only Applications are present") {

      Given("There are applications in the collection")

      AuthStub.authorised()
      dropStores()
      storeCases(CaseData.createBtaCardData(eori, 10, 9, 0, 0): _*)

      When("I call the BTA Card endpoint for the counts")
      val result = Http(s"$serviceUrl/bta-card")
        .header("Authorization", "Auth")
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The EORI should be present")
      Json.parse(result.body).as[BtaCard].eori shouldBe eori

      And("The Applications counts should be correct")
      Json.parse(result.body).as[BtaCard].applications shouldBe Some(BtaApplications(10, 9))

      And("The Rulings counts should be None")
      Json.parse(result.body).as[BtaCard].rulings shouldBe None
    }

    Scenario("BTA Card counts where no counts present") {

      Given("There are no applications or rulings in the collection")

      AuthStub.authorised()
      dropStores()

      When("I call the BTA Card endpoint for the counts")
      val result = Http(s"$serviceUrl/bta-card")
        .header("Authorization", "Auth")
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The EORI should be present")
      Json.parse(result.body).as[BtaCard].eori shouldBe eori

      And("The Applications counts should be None")
      Json.parse(result.body).as[BtaCard].applications shouldBe None

      And("The Rulings counts should be None")
      Json.parse(result.body).as[BtaCard].rulings shouldBe None
    }

    Scenario("BTA Card counts where are user does not supply an Authorization Header") {

      Given("There is no Auth Header")
      dropStores()

      When("I call the BTA Card endpoint for the counts")
      val result = Http(s"$serviceUrl/bta-card")
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should be 403")
      result.code shouldEqual FORBIDDEN
    }

    Scenario("BTA Card counts where are user does not pass Auth") {

      Given("Auth call fails")

      AuthStub.unauthorised()

      dropStores()

      When("I call the BTA Card endpoint for the counts")
      val result = Http(s"$serviceUrl/bta-card")
        .header("Authorization", "Auth")
        .method(HttpVerbs.GET)
        .asString

      Then("The response code should be 403")
      result.code shouldEqual FORBIDDEN
    }
  }
}
