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

import java.util.UUID

import play.api.libs.json.Json
import play.api.http.HttpVerbs
import play.api.http.Status.{NO_CONTENT, NOT_FOUND, OK}
import scalaj.http.Http
import uk.gov.hmrc.bindingtariffclassification.model.JsonFormatters.formatEvent
import uk.gov.hmrc.bindingtariffclassification.todelete.EventData._

class EventSpec extends BaseFeatureSpec {

  override lazy val port = 14682
  protected val serviceUrl = s"http://localhost:$port"

  private val caseRef = UUID.randomUUID().toString
  private val e1 = createCaseStatusChangeEvent(caseReference = caseRef)
  private val e2 = createNoteEvent(caseReference = caseRef)


  feature("Delete All") {

    scenario("Clear Collection") {

      Given("There are some documents in the collection")
      storeEvents(e1, e2)

      When("I delete all documents")
      val deleteResult = Http(s"$serviceUrl/events")
        .method(HttpVerbs.DELETE)
        .asString

      Then("The response code should be 204")
      deleteResult.code shouldEqual NO_CONTENT

      And("The response body is empty")
      deleteResult.body shouldBe ""

      And("No documents exist in the mongo collection")
      eventStoreSize shouldBe 0
    }

  }


  feature("Get Event by Id") {

    scenario("Get existing event") {

      Given("There is an event in the database")
      storeEvents(e1)

      When("I get an event")
      val result = Http(s"$serviceUrl/events/${e1.id}").asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The expected event is returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(e1)
    }

    scenario("Get a non-existing event") {

      When("I get an event")
      val result = Http(s"$serviceUrl/events/${e1.id}").asString

      Then("The response code should be NOT FOUND")
      result.code shouldEqual NOT_FOUND
    }

  }

  feature("Get Events by case reference") {

    scenario("No events found") {

      When("I get the events for a specific case reference")
      val result = Http(s"$serviceUrl/events/case-reference/$caseRef").asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("An empty sequence is returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq.empty)
    }

    scenario("Events found") {

      Given("There are some events for a specific case")
      storeEvents(e1, e2)

      When("I get the events for that specific case")
      val result = Http(s"$serviceUrl/events/case-reference/$caseRef").asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("All events are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Seq(e1, e2))
    }

  }

}
