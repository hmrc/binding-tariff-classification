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
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.JsonFormatters._
import uk.gov.hmrc.bindingtariffclassification.todelete.CaseData._

class CaseSpec extends BaseFeatureSpec {

  override lazy val port = 14681
  protected val serviceUrl = s"http://localhost:$port"

  private val q1 = "queue1"
  private val u1 = "user1"
  private val c1 = createCase(app = createBTIApplication, queue = Some(q1), assignee = Some(u1))
  private val status = CaseStatus.CANCELLED
  private val c1_updated = c1.copy(status = status)
  private val c2 = createCase(app = createBTIApplication)

  private val c1Json = Json.toJson(c1)
  private val c1UpdatedJson = Json.toJson(c1_updated)


  feature("Create Case") {

    scenario("Create a new case") {

      When("I create a new case")
      val result = Http(s"$serviceUrl/cases")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .postData(c1Json.toString()).asString

      Then("The case is returned in the JSON response")
      Json.parse(result.body) shouldBe c1Json

      And("The response code should be created")
      result.code shouldEqual CREATED
    }

    scenario("Create an existing case") {

      Given("There is a case in the database")
      storeCases(c1)

      When("I create a case that already exist")
      val result = Http(s"$serviceUrl/cases")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .postData(c1Json.toString()).asString

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


  feature ("Update Case Status") {

    scenario("Update the status for an non-existing case") {

      When("I update the status for a non-existing case")
      val result = Http(s"$serviceUrl/cases/${c1.reference}/status")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .put(Json.toJson(Status(status)).toString()).asString

      Then("The response code should be NOT FOUND")
      result.code shouldEqual NOT_FOUND
    }

    scenario("Update the status of an existing case, but without setting a new value for the status") {

      Given("There is a case in the database")
      storeCases(c1)

      When("I update the status setting it to its current value")
      val result = Http(s"$serviceUrl/cases/${c1.reference}/status")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .put(Json.toJson(Status(c1.status)).toString()).asString

      Then("The response code should be NOT FOUND")
      result.code shouldEqual NOT_FOUND
    }

    scenario("Update the status of an existing case, setting its value to a different status") {

      Given("There is a case in the database")
      storeCases(c1)

      When("I update the status")
      val caseResult = Http(s"$serviceUrl/cases/${c1.reference}/status")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .put(Json.toJson(Status(status)).toString()).asString

      Then("The response code should be NOT FOUND")
      caseResult.code shouldEqual OK

      And("The updated case is returned in the JSON response")
      Json.parse(caseResult.body) shouldBe c1UpdatedJson

      And("A case status change event has been created")
      val eventResult = Http(s"$serviceUrl/events/case-reference/${c1.reference}")
        .headers(Seq(CONTENT_TYPE -> JSON))
        .asString

      eventResult.code shouldEqual OK
      val events = Json.parse(eventResult.body).as[Seq[Event]]
      events.size shouldBe 1
      val event = events.head
      event.details shouldBe CaseStatusChange(from = CaseStatus.NEW, to = CaseStatus.CANCELLED)
      event.userId shouldBe u1
      event.caseReference shouldBe c1.reference
    }

  }

  feature("Get Case") {

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


  feature("Get Cases") {

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

  // TODO: test all possible combinations of get()

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


  feature("Get Cases sorted by elapsed days") {

    scenario("Sorting cases by elapsed days") {

      When("I get all cases sorted by elapsed days")
      val result = Http(s"$serviceUrl/cases?sort_by=elapsed-days").asString

      Then("The response code should be 500")
      result.code shouldEqual INTERNAL_SERVER_ERROR
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
      val result = Http(s"$serviceUrl/cases?assignee_id=$u1").asString

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
      val result = Http(s"$serviceUrl/cases?assignee_id=$u1&queue_id=$q1").asString

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

}
