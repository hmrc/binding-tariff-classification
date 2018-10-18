package uk.gov.hmrc.component

import it.uk.gov.hmrc.component.BaseFeatureSpec
import play.api.libs.json.Json
import scalaj.http.Http
import it.uk.gov.hmrc.component.data.CaseData.{createBTIApplication, createCase}

class CaseSpec extends BaseFeatureSpec {

  val userId = "userId"
  val accessToken = "access_token"
  val authToken = "auth_token"
  val caseModel = createCase(createBTIApplication)


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
