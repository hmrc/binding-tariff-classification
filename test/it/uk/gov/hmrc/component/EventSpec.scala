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

import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status.{NO_CONTENT, OK}
import play.api.http.{HttpVerbs, Status}
import play.api.libs.json.Json
import scalaj.http.Http
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters.{formatEvent, formatNewEventRequest}
import uk.gov.hmrc.bindingtariffclassification.model.{Event, NewEventRequest, Note, Operator}
import util.CaseData.createCase
import util.EventData._

class EventSpec extends BaseFeatureSpec {

  override lazy val port = 14682
  protected val serviceUrl = s"http://localhost:$port"

  private val caseRef = UUID.randomUUID().toString
  private val c1 = createCase(r = caseRef)
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

  feature("Get Events by case reference") {

    scenario("No events found") {
      Given("There is a case")
      storeCases(c1)

      When("I get the events for a specific case reference")
      val result = Http(s"$serviceUrl/cases/$caseRef/events").asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("An empty sequence is returned in the JSON response")
      Json.parse(result.body).toString() shouldBe "[]"
    }

    scenario("Events found in any order") {

      Given("There is a case with events")
      storeCases(c1)
      storeEvents(e1, e2)

      When("I get the events for that specific case")
      val result = Http(s"$serviceUrl/cases/$caseRef/events").asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("All events are returned in the JSON response")

      val responseEvent: Seq[Event] = Json.parse(result.body).as[Seq[Event]]

      responseEvent.map(_.id) should contain theSameElementsAs Seq(e1.id, e2.id)
    }

  }

  feature("Create Event by case reference") {
    scenario("Create new event") {
      Given("An existing Case")
      storeCases(c1)

      When("I create an Event")
      val payload = NewEventRequest(Note(Some("Note")), Operator("user-id", Some("user name")))
      val result = Http(s"$serviceUrl/cases/$caseRef/events")
        .headers(Seq(CONTENT_TYPE -> "application/json"))
        .postData(Json.toJson(payload).toString()).asString

      Then("The response code should be created")
      result.code shouldEqual Status.CREATED

      And("The event is returned in the JSON response")
      val responseEvent = Json.parse(result.body).as[Event]
      responseEvent.caseReference shouldBe caseRef
    }
  }

}
