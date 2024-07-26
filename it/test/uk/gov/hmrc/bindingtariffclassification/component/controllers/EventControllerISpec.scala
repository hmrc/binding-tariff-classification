/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.component.controllers

import org.apache.pekko.stream.Materializer
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status.{NO_CONTENT, OK}
import play.api.http.{ContentTypes, Status}
import play.api.libs.json.Json
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import uk.gov.hmrc.bindingtariffclassification.component.BaseFeatureSpec
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters.{formatEvent, formatNewEventRequest}
import uk.gov.hmrc.bindingtariffclassification.model._
import util.CaseData.createCase
import util.EventData._

import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class EventControllerISpec extends BaseFeatureSpec {

  protected val serviceUrl = s"http://localhost:$port"

  implicit val mat: Materializer        = app.materializer
  val httpClient: StandaloneAhcWSClient = StandaloneAhcWSClient()

  private val caseRef = UUID.randomUUID().toString
  private val c1      = adaptCaseInstantFormat(createCase(r = caseRef))
  private val e1Base  = createCaseStatusChangeEvent(caseReference = caseRef)
  private val e1      = e1Base.copy(timestamp = e1Base.timestamp.truncatedTo(ChronoUnit.MILLIS))
  private val e2Base  = createNoteEvent(caseReference = caseRef)
  private val e2      = e2Base.copy(timestamp = e2Base.timestamp.truncatedTo(ChronoUnit.MILLIS))

  Feature("Delete All") {

    Scenario("Clear Collection") {

      Given("There are some documents in the collection")
      storeEvents(e1, e2)

      When("I delete all documents")
      val responseFuture = httpClient
        .url(s"$serviceUrl/events")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .delete()
      val deleteResult = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 204")
      deleteResult.status.intValue() shouldEqual NO_CONTENT

      And("The response body is empty")
      deleteResult.body shouldBe ""

      And("No documents exist in the mongo collection")
      eventStoreSize shouldBe 0
    }

  }

  Feature("Get Events by case reference") {

    Scenario("No events found") {

      Given("There is a case")
      storeCases(c1)

      When("I get the events for a specific case reference")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases/$caseRef/events")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue() shouldEqual OK

      And("An empty sequence is returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Event])
    }

    Scenario("Events found in any order") {

      Given("There is a case with events")
      storeCases(c1)
      storeEvents(e1)

      When("I get the events for that specific case")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases/$caseRef/events")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue() shouldEqual OK

      And("All events are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(e1)))
    }

  }

  Feature("Search Events") {

    Scenario("No events found") {
      When("I get the events")
      val responseFuture = httpClient
        .url(s"$serviceUrl/events")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue() shouldEqual OK

      And("An empty sequence is returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Event])
    }

    Scenario("Returns all Events") {
      Given("There is a case with events")
      storeCases(c1)
      storeEvents(e1)

      When("I get the events for that specific case")
      val responseFuture = httpClient
        .url(s"$serviceUrl/events")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue() shouldEqual OK

      And("All events are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(e1)))
    }

    Scenario("Returns Events by reference") {
      Given("There is a case with events")
      storeCases(c1)
      storeEvents(e1)

      When("I get the events for that specific case")
      val responseFuture = httpClient
        .url(s"$serviceUrl/events?case_reference=${c1.reference}")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue() shouldEqual OK

      And("All events are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(e1)))
    }

    Scenario("Returns Events by type") {
      Given("There is a case with events")
      storeCases(c1)
      storeEvents(e1)
      storeEvents(e2)

      When("I get the events for that specific case")
      val responseFuture = httpClient
        .url(s"$serviceUrl/events?type=${e1.details.`type`}")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue() shouldEqual OK

      And("All events are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(e1)))
    }

  }

  Feature("Create Event by case reference") {

    Scenario("Create new event") {

      Given("An existing Case")
      storeCases(c1)

      When("I create an Event")
      val payload = NewEventRequest(Note("Note"), Operator("user-id", Some("user name")))

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases/$caseRef/events")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization, CONTENT_TYPE -> ContentTypes.JSON)
        .post(Json.toJson(payload).toString())
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be created")
      result.status.intValue() shouldEqual Status.CREATED

      And("The event is returned in the JSON response")
      val responseEvent = Json.parse(result.body).as[Event]
      responseEvent.caseReference shouldBe caseRef
    }

  }

  Feature("Add a case created event") {

    Scenario("Create new event") {
      Given("A case that will get created")
      storeCases(c1)

      When("I create an Event")
      val payload = NewEventRequest(CaseCreated("Liability case created"), Operator("user-id", Some("user name")))
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases/$caseRef/events")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization, CONTENT_TYPE -> ContentTypes.JSON)
        .post(Json.toJson(payload).toString())
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be CREATED")
      result.status.intValue() shouldEqual Status.CREATED

      And("The event is returned in the JSON response")
      val responseEvent = Json.parse(result.body).as[Event]
      responseEvent.caseReference shouldBe caseRef

      val caseCreatedEvent = responseEvent.details.asInstanceOf[CaseCreated]
      caseCreatedEvent.comment shouldBe "Liability case created"
    }
  }
  private def adaptCaseInstantFormat(_case: Case): Case = {
    val caseBaseDecision    = _case.decision
    val caseBaseApplication = _case.application
    _case.copy(
      createdDate = _case.createdDate.truncatedTo(ChronoUnit.MILLIS),
      dateOfExtract = _case.dateOfExtract.map(_.truncatedTo(ChronoUnit.MILLIS)),
      decision = caseBaseDecision.map { desc =>
        desc.copy(
          effectiveStartDate = desc.effectiveStartDate.map(_.truncatedTo(ChronoUnit.MILLIS)),
          effectiveEndDate = desc.effectiveEndDate.map(_.truncatedTo(ChronoUnit.MILLIS)),
          decisionPdf = desc.decisionPdf.map { attch =>
            attch.copy(
              timestamp = attch.timestamp.truncatedTo(ChronoUnit.MILLIS)
            )
          }
        )
      },
      application = caseBaseApplication match {
        case liabilityApp: LiabilityOrder =>
          liabilityApp.copy(
            entryDate = liabilityApp.entryDate.map(_.truncatedTo(ChronoUnit.MILLIS)),
            dateOfReceipt = liabilityApp.dateOfReceipt.map(_.truncatedTo(ChronoUnit.MILLIS))
          )
        case btiApp: BTIApplication =>
          btiApp.copy(
            agent = btiApp.agent.map(agent =>
              agent.copy(
                letterOfAuthorisation = agent.letterOfAuthorisation.map(att =>
                  att.copy(
                    timestamp = att.timestamp.truncatedTo(ChronoUnit.MILLIS)
                  )
                )
              )
            ),
            applicationPdf =
              btiApp.applicationPdf.map(pdf => pdf.copy(timestamp = pdf.timestamp.truncatedTo(ChronoUnit.MILLIS)))
          )
        case _ => caseBaseApplication
      }
    )
  }
}
