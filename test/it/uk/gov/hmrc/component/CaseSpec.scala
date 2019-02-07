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

package uk.gov.hmrc.component

import java.time.Instant

import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.HttpVerbs
import play.api.http.Status._
import play.api.libs.json.Json
import scalaj.http.{Http, HttpResponse}
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import util.CaseData._
import util.Matchers.roughlyBe

class CaseSpec extends BaseFeatureSpec {

  override lazy val port = 14681
  protected val serviceUrl = s"http://localhost:$port"

  private val q1 = "queue1"
  private val u1 = Operator("user1")
  private val c0 = createNewCase(app = createBasicBTIApplication)
  private val c1 = createCase(app = createBasicBTIApplication, queue = Some(q1), assignee = Some(u1))
  private val status = CaseStatus.CANCELLED
  private val c1_updated = c1.copy(status = status)
  private val c2 = createCase(app = createLiabilityOrder,
    decision = Some(createDecision()),
    attachments = Seq(createAttachment,createAttachmentWithOperator),
    keywords = Set("BIKE", "MTB", "HARDTAIL"))
  private val c3 = createNewCaseWithExtraFields()
  private val c4 = createNewCase(app = createBTIApplicationWithAllFields)
  private val c5 = createCase(app = createBasicBTIApplication.copy(holder = eORIDetailForNintedo))
  private val c6 = createCase(decision = Some(createDecision(effectiveEndDate = Some(Instant.now().plusSeconds(60)))))
  private val c7 = createCase(decision = Some(createDecision(goodsDescription = "LAPTOP")))
  private val c8 = createCase(decision = Some(createDecision(methodCommercialDenomination = Some("this is a great laptop from Mexico"))))
  private val c9 = createCase(keywords = Set("MTB", "BICYCLE"))

  private val c0Json = Json.toJson(c0)
  private val c1Json = Json.toJson(c1)
  private val c1UpdatedJson = Json.toJson(c1_updated)
  private val c3Json = Json.toJson(c3)
  private val c4Json = Json.toJson(c4)


  feature("Delete All") {

    scenario("Clear Collection") {

      Given("There are some documents in the collection")
      storeCases(c1, c2)

      When("I delete all documents")
      val deleteResult = Http(s"$serviceUrl/cases")
        .method(HttpVerbs.DELETE)
        .asString

      Then("The response code should be 204")
      deleteResult.code shouldEqual NO_CONTENT

      And("The response body is empty")
      deleteResult.body shouldBe ""

      And("No documents exist in the mongo collection")
      caseStoreSize shouldBe 0
    }

  }


  feature("Create Case") {

    scenario("Create a new case") {

      When("I create a new case")
      val result: HttpResponse[String] = Http(s"$serviceUrl/cases")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .postData(c0Json.toString()).asString

      Then("The response code should be created")
      result.code shouldEqual CREATED

      And("The case is returned in the JSON response")
      val responseCase = Json.parse(result.body).as[Case]
      responseCase.reference shouldBe "1"
      responseCase.status shouldBe CaseStatus.NEW
    }

    scenario("Extra fields are ignored when creating a case") {
      When("I create a new case with extra fields")
      val result: HttpResponse[String] = Http(s"$serviceUrl/cases")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .postData(c3Json.toString()).asString

      Then("The response code should be created")
      result.code shouldEqual CREATED

      And("The case is returned in the JSON response")
      val responseCase = Json.parse(result.body).as[Case]
      responseCase.reference shouldBe "1"
      responseCase.status shouldBe CaseStatus.NEW
      responseCase.createdDate should roughlyBe(Instant.now())
      responseCase.assignee shouldBe None
      responseCase.queueId shouldBe None
      responseCase.decision shouldBe None
      responseCase.closedDate shouldBe None
    }

    scenario("Create a new case with all fields") {

      When("I create a new case")
      val result: HttpResponse[String] = Http(s"$serviceUrl/cases")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .postData(c4Json.toString()).asString

      Then("The response code should be created")
      result.code shouldEqual CREATED

      And("The case is returned in the JSON response")
      val responseCase = Json.parse(result.body).as[Case]
      responseCase.reference shouldBe "1"
      responseCase.status shouldBe CaseStatus.NEW
    }

  }


  feature("Update Case") {

    scenario("Update an non-existing case") {

      When("I update a non-existing case")
      val result = Http(s"$serviceUrl/cases/${c1.reference}")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .put(c1Json.toString()).asString

      Then("The response code should be NOT FOUND")
      result.code shouldEqual NOT_FOUND
    }

    scenario("Update an existing case") {

      Given("There is a case in the database")
      storeCases(c1)

      When("I update an existing case")
      val result = Http(s"$serviceUrl/cases/${c1.reference}")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .put(c1UpdatedJson.toString()).asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The case is returned in the JSON response")
      Json.parse(result.body) shouldBe c1UpdatedJson
    }

  }


  feature("Get Case by Reference") {

    scenario("Get existing case") {

      Given("There is a case in the database")
      storeCases(c1)

      When("I get a case")
      val result = Http(s"$serviceUrl/cases/${c1.reference}").asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The case is returned in the JSON response")
      Json.parse(result.body) shouldBe c1Json
    }

    scenario("Get a non-existing case") {

      When("I get a case")
      val result = Http(s"$serviceUrl/cases/${c1.reference}").asString

      Then("The response code should be NOT FOUND")
      result.code shouldEqual NOT_FOUND
    }

  }


  feature("Get All Cases") {

    scenario("Get all cases") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get all cases")
      val result = Http(s"$serviceUrl/cases").asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq(c1, c2))
    }

    scenario("Get no cases") {

      Given("There are no cases in the database")

      When("I get all cases")
      val result = Http(s"$serviceUrl/cases").asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("No cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq.empty[Case])
    }

  }


  feature("Get Cases by Queue Id") {

    scenario("Filtering cases that have undefined queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by queue id")
      val result = Http(s"$serviceUrl/cases?queue_id=none").asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq(c2))
    }

    scenario("Filtering cases by a valid queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by queue id")
      val result = Http(s"$serviceUrl/cases?queue_id=$q1").asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq(c1))
    }

    scenario("Filtering cases by a wrong queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by queue id")
      val result = Http(s"$serviceUrl/cases?queue_id=wrong").asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("No cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq.empty[Case])
    }

  }


  feature("Get Cases by Assignee Id") {

    scenario("Filtering cases that have undefined assigneeId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id")
      val result = Http(s"$serviceUrl/cases?assignee_id=none").asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq(c2))
    }

    scenario("Filtering cases by a valid assigneeId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id")
      val result = Http(s"$serviceUrl/cases?assignee_id=${u1.id}").asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq(c1))
    }

    scenario("Filtering cases by a wrong assigneeId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id")
      val result = Http(s"$serviceUrl/cases?assignee_id=wrong").asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("No cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq.empty[Case])
    }

  }


  feature("Get Cases by Assignee Id and Queue Id") {

    scenario("Filtering cases that have undefined assigneeId and undefined queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id and queue id")
      val result = Http(s"$serviceUrl/cases?assignee_id=none&queue_id=none").asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq(c2))
    }

    scenario("Filtering cases by a valid assigneeId and a valid queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id and queue id")
      val result = Http(s"$serviceUrl/cases?assignee_id=${u1.id}&queue_id=$q1").asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq(c1))
    }

    scenario("Filtering cases by a wrong assigneeId and a valid queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id")
      val result = Http(s"$serviceUrl/cases?assignee_id=_a_&queue_id=$q1").asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("No cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq.empty[Case])
    }

  }


  feature("Get Cases by statuses") {

    scenario("No matches") {

      storeCases(c1_updated, c2, c5)

      val result = Http(s"$serviceUrl/cases?status=SUSPENDED").asString

      result.code shouldEqual OK
      result.body.toString shouldBe "[]"
    }

    scenario("Filtering cases by single status") {

      storeCases(c1_updated, c2, c5)

      val result = Http(s"$serviceUrl/cases?status=NEW").asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Seq(c2,c5))
    }

    scenario("Filtering cases by multiple statuses") {

      storeCases(c1_updated, c2, c5)

      val result = Http(s"$serviceUrl/cases?status=NEW&status=CANCELLED").asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Seq(c1_updated,c2,c5))
    }

    scenario("Filtering cases by multiple statuses - comma separated") {

      storeCases(c1_updated, c2, c5)

      val result = Http(s"$serviceUrl/cases?status=NEW,CANCELLED").asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Seq(c1_updated,c2,c5))
    }

  }


  feature("Get Cases by keywords") {

    scenario("No matches") {

      storeCases(c2, c9)

      val result = Http(s"$serviceUrl/cases?keyword=PHONE").asString

      result.code shouldEqual OK
      result.body.toString shouldBe "[]"
    }

    scenario("Filtering cases by single keyword") {

      storeCases(c2, c5, c9)

      val result = Http(s"$serviceUrl/cases?keyword=MTB").asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Seq(c2, c9))
    }

    scenario("Filtering cases by multiple keywords") {

      storeCases(c2, c5, c9)

      val result = Http(s"$serviceUrl/cases?keyword=MTB&keyword=HARDTAIL").asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Seq(c2))
    }

    scenario("Filtering cases by multiple keywords - comma separated") {

      storeCases(c2, c5, c9)

      val result = Http(s"$serviceUrl/cases?keyword=MTB,HARDTAIL").asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Seq(c2))
    }

  }


  feature("Get Cases by trader name") {

    scenario("Filtering cases by trader name") {

      storeCases(c1, c2, c5)

      val result = Http(s"$serviceUrl/cases?trader_name=John%20Lewis").asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Seq(c1,c2))
    }

    // currently not implemented
    scenario("Case-insensitive search") {

      storeCases(c1)

      val result = Http(s"$serviceUrl/cases?trader_name=john%20Lewis").asString

      result.code shouldEqual OK
      result.body.toString shouldBe "[]"
    }

    // currently not implemented
    scenario("Search by substring") {

      storeCases(c1)

      val result = Http(s"$serviceUrl/cases?trader_name=Lewis").asString

      result.code shouldEqual OK
      result.body.toString shouldBe "[]"
    }

    scenario("No matches") {

      storeCases(c1)

      val result = Http(s"$serviceUrl/cases?trader_name=Albert").asString

      result.code shouldEqual OK
      result.body.toString shouldBe "[]"
    }

    scenario("Filtering cases that have undefined trader name") {

      storeCases(c1, c2, c5)

      val result = Http(s"$serviceUrl/cases?trader_name=").asString

      result.code shouldEqual OK
      result.body.toString shouldBe "[]"
    }

  }


  feature("Get Cases by Min Decision End Date") {

    scenario("Filtering cases by Min Decision End Date") {

      storeCases(c1, c6)

      val result = Http(s"$serviceUrl/cases?min_decision_end=1970-01-01T00:00:00Z").asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Seq(c6))
    }

    scenario("Filtering cases by Min Decision End Date - filters decisions in the past") {

      storeCases(c1, c6)

      val result = Http(s"$serviceUrl/cases?min_decision_end=3000-01-01T00:00:00Z").asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Seq.empty[Case])
    }

  }


  feature("Get Cases by commodity code") {

    scenario("filtering by non-existing commodity code") {

      storeCases(c1, c2, c5)

      val result = Http(s"$serviceUrl/cases?commodity_code=66").asString

      result.code shouldEqual OK
      result.body.toString shouldBe "[]"
    }

    scenario("filtering by existing commodity code") {

      storeCases(c1, c2, c5, c6)

      val result = Http(s"$serviceUrl/cases?commodity_code=12345678").asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Seq(c2,c6))
    }

    scenario("Starts-with match") {

      storeCases(c1, c2, c5, c6)

      val result = Http(s"$serviceUrl/cases?commodity_code=123").asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Seq(c2,c6))
    }

    scenario("Contains-match does not return any result") {

      storeCases(c2, c6)

      val result = Http(s"$serviceUrl/cases?commodity_code=456").asString

      result.code shouldEqual OK
      result.body.toString shouldBe "[]"
    }

  }


  feature("Get Cases by good description") {

    scenario("No matches") {

      storeCases(c1, c2, c5)

      val result = Http(s"$serviceUrl/cases?good_description=laptop").asString

      result.code shouldEqual OK
      result.body.toString shouldBe "[]"
    }

    scenario("Filtering by existing good description") {

      storeCases(c1, c2, c7)

      val result = Http(s"$serviceUrl/cases?good_description=LAPTOP").asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Seq(c7))
    }

    scenario("Case-insensitive search") {

      storeCases(c1, c2, c7)

      val result = Http(s"$serviceUrl/cases?good_description=laptop").asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Seq(c7))
    }

    scenario("Filtering by substring") {

      storeCases(c1, c2, c7, c8)

      val result = Http(s"$serviceUrl/cases?good_description=laptop").asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Seq(c7, c8))
    }
  }


  feature("Get Cases by multiple parameters") {
    // TODO
  }


  feature("Get Cases sorted by commodity code") {

    val caseWithEmptyCommCode = createCase().copy(decision = None)
    val caseY1 = createCase().copy(decision = Some(createDecision(bindingCommodityCode = "777")))
    val caseY2 = createCase().copy(decision = Some(createDecision(bindingCommodityCode = "777")))
    val caseZ = createCase().copy(decision = Some(createDecision(bindingCommodityCode = "1111111111")))

    scenario("Sorting default - ascending order") {
      Given("There are few cases in the database")
      storeCases(caseY2, caseWithEmptyCommCode, caseY1, caseZ)

      When("I get all cases sorted by commodity code")
      val result = Http(s"$serviceUrl/cases?sort_by=commodity-code").asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq(caseWithEmptyCommCode, caseZ, caseY2, caseY1))
    }

    scenario("Sorting in ascending order") {
      Given("There are few cases in the database")
      storeCases(caseY1, caseWithEmptyCommCode, caseY2, caseZ)

      When("I get all cases sorted by commodity code")
      val result = Http(s"$serviceUrl/cases?sort_by=commodity-code&sort_direction=asc").asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq(caseWithEmptyCommCode, caseZ, caseY1, caseY2))
    }

    scenario("Sorting in descending order") {
      Given("There are few cases in the database")
      storeCases(caseZ, caseWithEmptyCommCode, caseY1, caseY2)

      When("I get all cases sorted by commodity code")
      val result = Http(s"$serviceUrl/cases?sort_by=commodity-code&sort_direction=desc").asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq(caseY1, caseY2, caseZ, caseWithEmptyCommCode))
    }

  }


  feature("Get Cases sorted by days elapsed") {

    val oldCase = c1.copy(daysElapsed = 1)
    val newCase = c2.copy(daysElapsed = 0)

    scenario("Sorting default - ascending order") {
      Given("There are few cases in the database")
      storeCases(oldCase, newCase)

      When("I get all cases sorted by elapsed days")
      val result = Http(s"$serviceUrl/cases?sort_by=days-elapsed").asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq(newCase, oldCase))
    }

    scenario("Sorting in ascending order") {
      Given("There are few cases in the database")
      storeCases(oldCase, newCase)

      When("I get all cases sorted by elapsed days")
      val result = Http(s"$serviceUrl/cases?sort_by=days-elapsed&sort_direction=asc").asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq(newCase, oldCase))
    }

    scenario("Sorting in descending order") {
      Given("There are few cases in the database")
      storeCases(oldCase, newCase)

      When("I get all cases sorted by elapsed days")
      val result = Http(s"$serviceUrl/cases?sort_by=days-elapsed&sort_direction=desc").asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq(oldCase, newCase))
    }

  }


}
