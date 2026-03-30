/*
 * Copyright 2025 HM Revenue & Customs
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

import org.apache.pekko.stream.Materializer

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant}
import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import uk.gov.hmrc.bindingtariffclassification.model._
import util.CaseData._
import util.Matchers.roughlyBe
import play.api.libs.ws.DefaultBodyWritables.writeableOf_String
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters.{formatCase, formatCorrespondence, formatNewCase}
import play.api.libs.ws.DefaultBodyReadables.readableAsString

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class CaseSpec extends BaseFeatureSpec {

  protected val serviceUrl = s"http://localhost:$port"

  given mat: Materializer               = app.materializer
  val httpClient: StandaloneAhcWSClient = StandaloneAhcWSClient()

  private val clock = Clock.systemUTC()
  private val q1    = "queue1"
  private val u1    = Operator("user1")
  private val c0    = createNewCase(app = createBasicBTIApplication)
  private val c1 = adaptCaseInstantFormat(
    createCase(app = createBasicBTIApplication, assignee = Some(u1)).copy(queueId = Some(q1))
  )
  private val status     = CaseStatus.CANCELLED
  private val c1_updated = c1.copy(status = status)
  private val c2 = adaptCaseInstantFormat(
    createCase(
      r = "case_ref_2",
      app = createLiabilityOrder,
      decision = Some(createDecision()),
      attachments = Seq(createAttachment, createAttachmentWithOperator),
      keywords = Set("BIKE", "MTB", "HARDTAIL")
    )
  )
  private val c2CreateWithExtraFields = createNewCase(app = createLiabilityOrderWithExtraFields)
  private val correspondenceCase      = createNewCase(app = createCorrespondenceApplication)
  private val miscCase                = createNewCase(app = createMiscApplication)
  private val c2WithExtraFields = adaptCaseInstantFormat(
    createCase(
      r = "case_ref_2",
      app = createLiabilityOrderWithExtraFields,
      decision = Some(createDecision()),
      attachments = Seq(createAttachment, createAttachmentWithOperator),
      keywords = Set("BIKE", "MTB", "HARDTAIL")
    )
  )
  private val c3 = adaptCaseInstantFormat(createNewCaseWithExtraFields())
  private val c4 = createNewCase(app = createBTIApplicationWithAllFields())
  private val c5 = adaptCaseInstantFormat(
    createCase(r = "case_ref_5", app = createBasicBTIApplication.copy(holder = eORIDetailForNintedo))
  )
  private val c6_live = adaptCaseInstantFormat(
    createCase(
      status = CaseStatus.COMPLETED,
      decision = Some(createDecision(effectiveEndDate = Some(Instant.now(clock).plusSeconds(3600 * 24))))
    )
  )
  private val c6_expired = adaptCaseInstantFormat(
    createCase(
      status = CaseStatus.COMPLETED,
      decision = Some(createDecision(effectiveEndDate = Some(Instant.now(clock).minusSeconds(3600 * 24))))
    )
  )
  private val c7 = adaptCaseInstantFormat(createCase(decision = Some(createDecision(goodsDescription = "LAPTOP"))))
  private val c8 =
    adaptCaseInstantFormat(
      createCase(decision = Some(createDecision(methodCommercialDenomination = Some("laptop from Mexico"))))
    )
  private val c9 = adaptCaseInstantFormat(
    createCase(decision = Some(createDecision(justification = "this LLLLaptoppp")))
  )
  private val c10 = adaptCaseInstantFormat(createCase(keywords = Set("MTB", "BICYCLE")))
  private val c11 = adaptCaseInstantFormat(
    createCase(
      decision = Some(
        createDecision(
          goodsDescription = "LAPTOP",
          effectiveStartDate = Some(Instant.now().minus(3, ChronoUnit.DAYS)),
          effectiveEndDate = Some(Instant.now().minus(1, ChronoUnit.DAYS))
        )
      ),
      status = CaseStatus.COMPLETED
    )
  )
  private val c12 = adaptCaseInstantFormat(
    createCase(
      decision = Some(
        createDecision(
          goodsDescription = "SPANNER",
          effectiveStartDate = Some(Instant.now().minus(3, ChronoUnit.DAYS)),
          effectiveEndDate = Some(Instant.now().minus(1, ChronoUnit.DAYS))
        )
      ),
      status = CaseStatus.COMPLETED
    )
  )
  private val c13 = adaptCaseInstantFormat(
    createCase(
      decision = Some(
        createDecision(
          goodsDescription = "LAPTOP",
          effectiveStartDate = Some(Instant.now()),
          effectiveEndDate = Some(Instant.now().plus(1, ChronoUnit.DAYS))
        )
      ),
      status = CaseStatus.COMPLETED
    )
  )
  private val c0Json                      = Json.toJson(c0)
  private val c1Json                      = Json.toJson(c1)
  private val c1UpdatedJson               = Json.toJson(c1_updated)
  private val c3Json                      = Json.toJson(c3)
  private val c4Json                      = Json.toJson(c4)
  private val c2WithExtraFieldsJson       = Json.toJson(c2WithExtraFields)
  private val c2CreateWithExtraFieldsJson = Json.toJson(c2CreateWithExtraFields)
  private val correspondenceCaseJson      = Json.toJson(correspondenceCase)
  private val miscCaseJson                = Json.toJson(miscCase)

  Feature("Delete All") {

    Scenario("Clear Collection") {

      Given("There are some documents in the collection")
      storeCases(c1, c2)

      When("I delete all documents")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .delete()
      val deleteResult = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 204")
      deleteResult.status.intValue().shouldEqual(NO_CONTENT)

      And("The response body is empty")
      deleteResult.body.shouldBe("")

      And("No documents exist in the mongo collection")
      caseStoreSize.shouldBe(0)
    }

  }

  Feature("Create Case") {

    Scenario("Create a new case") {

      When("I create a new case")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization, CONTENT_TYPE -> JSON)
        .post(c0Json.toString())
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be created")
      result.status.intValue().shouldEqual(CREATED)

      And("The case is returned in the JSON response")
      val responseCase = Json.parse(result.body).as[Case]
      responseCase.reference.shouldBe("600000001")
      responseCase.status.shouldBe(CaseStatus.NEW)
    }

    Scenario("Extra fields are ignored when creating a case") {
      When("I create a new case with extra fields")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization, CONTENT_TYPE -> JSON)
        .post(c3Json.toString())
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be created")
      result.status.intValue().shouldEqual(CREATED)

      And("The case is returned in the JSON response")
      val responseCase = Json.parse(result.body).as[Case]
      responseCase.reference.shouldBe("600000001")
      responseCase.status.shouldBe(CaseStatus.NEW)
      responseCase.createdDate.should(roughlyBe(Instant.now()))
      responseCase.assignee.shouldBe(None)
      responseCase.queueId.shouldBe(None)
      responseCase.decision.shouldBe(None)
    }

    Scenario("Create a new case with all fields") {

      When("I create a new case")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization, CONTENT_TYPE -> JSON)
        .post(c4Json.toString())
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be created")
      result.status.intValue().shouldEqual(CREATED)

      And("The case is returned in the JSON response")
      val responseCase = Json.parse(result.body).as[Case]
      responseCase.reference.shouldBe("600000001")
      responseCase.status.shouldBe(CaseStatus.NEW)
    }

    Scenario("Create a new liability case with new fields DIT-1962") {

      When("I create a new liability case")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization, CONTENT_TYPE -> JSON)
        .post(c2CreateWithExtraFieldsJson.toString())
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be created")
      result.status.intValue().shouldEqual(CREATED)

      And("The case is returned in the JSON response")
      val responseCase = Json.parse(result.body).as[Case]
      responseCase.reference.shouldBe("800000001")
      responseCase.status.shouldBe(CaseStatus.NEW)
      responseCase.application.asLiabilityOrder.btiReference.shouldBe(Some("BTI-REFERENCE"))
      responseCase.application.asLiabilityOrder.repaymentClaim.get.dvrNumber.shouldBe(Some("DVR-123456"))
      responseCase.application.asLiabilityOrder.repaymentClaim.get.dateForRepayment.get.should(roughlyBe(Instant.now()))
      responseCase.application.asLiabilityOrder.dateOfReceipt.get.should(roughlyBe(Instant.now()))

      responseCase.application.asLiabilityOrder.traderContactDetails.get.shouldBe(
        TraderContactDetails(
          Some("email"),
          Some("phone"),
          Some(Address("Street Name", "Town", Some("County"), Some("P0ST C05E")))
        )
      )

      responseCase.application.asLiabilityOrder.agentName.shouldBe(Some("agent"))
      responseCase.application.asLiabilityOrder.port.shouldBe(Some("port"))
    }

    Scenario("Create a new Correspondence case") {

      When("I create a new Correspondence case")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization, CONTENT_TYPE -> JSON)
        .post(correspondenceCaseJson.toString())
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be created")
      result.status.intValue().shouldEqual(CREATED)

      And("The case is returned in the JSON response")
      val responseCase = Json.parse(result.body).as[Case]
      responseCase.reference.shouldBe("800000001")
      responseCase.status.shouldBe(CaseStatus.NEW)
      responseCase.application.asCorrespondence.summary.shouldBe("Laptop")
      responseCase.application.asCorrespondence.detailedDescription.shouldBe("Personal Computer")
      responseCase.application.asCorrespondence.address.shouldBe(
        Address(
          "23, Leyton St",
          "Leeds",
          Some("West Yorkshire"),
          Some("LS4 99AA")
        )
      )
      responseCase.application.asCorrespondence.contact.shouldBe(
        Contact(
          "Maurizio",
          "maurizio@me.com",
          Some("0123456789")
        )
      )
      responseCase.application.asCorrespondence.agentName.shouldBe(Some("agent"))
      responseCase.application.asCorrespondence.sampleToBeProvided.shouldBe(false)
      responseCase.application.asCorrespondence.sampleToBeReturned.shouldBe(false)
    }

    Scenario("Create a new Misc case") {

      When("I create a new Misc case")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization, CONTENT_TYPE -> JSON)
        .post(miscCaseJson.toString())
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be created")
      result.status.intValue().shouldEqual(CREATED)

      And("The case is returned in the JSON response")
      val responseCase = Json.parse(result.body).as[Case]
      responseCase.reference.shouldBe("800000001")
      responseCase.status.shouldBe(CaseStatus.NEW)
      responseCase.application.asMisc.name.shouldBe("name")
      responseCase.application.asMisc.contactName.shouldBe(Some("contactName"))
      responseCase.application.asMisc.caseType.shouldBe(MiscCaseType.HARMONISED)
      responseCase.application.asMisc.contact.shouldBe(Contact("Maurizio",     "maurizio@me.com", Some("0123456789")))
      responseCase.application.asMisc.sampleToBeProvided.shouldBe(false)
      responseCase.application.asMisc.sampleToBeReturned.shouldBe(false)
    }
  }

  Feature("Update Case") {

    Scenario("Update an non-existing case") {

      When("I update a non-existing case")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases/${c1.reference}")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization, CONTENT_TYPE -> JSON)
        .put(c1Json.toString())
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be NOT FOUND")
      result.status.intValue().shouldEqual(NOT_FOUND)
    }

    Scenario("Update an existing case") {

      Given("There is a case in the database")
      storeCases(c1)

      When("I update an existing case")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases/${c1.reference}")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization, CONTENT_TYPE -> JSON)
        .put(c1UpdatedJson.toString())
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue.shouldEqual(OK)

      And("The case is returned in the JSON response")
      Json.parse(result.body).shouldBe(c1UpdatedJson)
    }

    Scenario("Update an existing case with new fields DIT-1962") {

      Given("There is an existing case in the database")
      storeCases(c2)

      When("I update an existing case")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases/${c2.reference}")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization, CONTENT_TYPE -> JSON)
        .put(c2WithExtraFieldsJson.toString())
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("Response should be OK")
      result.status.intValue.shouldEqual(OK)

      And("The case is returned in the JSON response")
      Json.parse(result.body).shouldBe(c2WithExtraFieldsJson)
    }
  }

  Feature("Get Case by Reference") {

    Scenario("Get existing case") {

      Given("There is a case in the database")
      storeCases(c1)

      When("I get a case")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases/${c1.reference}")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue shouldEqual OK

      And("The case is returned in the JSON response")
      Json.parse(result.body) shouldBe c1Json
    }

    Scenario("Get a non-existing case") {

      When("I get a case")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases/${c1.reference}")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be NOT FOUND")
      result.status.intValue shouldEqual NOT_FOUND
    }

  }

  Feature("Get All Cases") {

    Scenario("Get all cases") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get all cases")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue shouldEqual OK

      And("The cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1, c2)))
    }

    Scenario("Get no cases") {

      Given("There are no cases in the database")

      When("I get all cases")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue shouldEqual OK

      And("No cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

  }

  Feature("Get Cases by Queue Id") {

    Scenario("Filtering cases that have undefined queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by queue id")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?queue_id=none")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2)))
    }

    Scenario("Filtering cases that have defined queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by queue id")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?queue_id=some")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1)))
    }

    Scenario("Filtering cases by a valid queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by queue id")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?queue_id=$q1")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1)))
    }

    Scenario("Filtering cases by a wrong queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by queue id")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?queue_id=wrong")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue shouldEqual OK

      And("No cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

  }

  Feature("Get Cases by Assignee Id") {

    Scenario("Filtering cases that have undefined assigneeId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?assignee_id=none")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2)))
    }

    Scenario("Filtering cases that have defined assigneeId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?assignee_id=some")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1)))
    }

    Scenario("Filtering cases by a valid assigneeId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?assignee_id=${u1.id}")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1)))
    }

    Scenario("Filtering cases by a wrong assigneeId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?assignee_id=wrong")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue shouldEqual OK

      And("No cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

  }

  Feature("Get Cases by Assignee Id and Queue Id") {

    Scenario("Filtering cases that have undefined assigneeId and undefined queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id and queue id")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?assignee_id=none&queue_id=none")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2)))
    }

    Scenario("Filtering cases by a valid assigneeId and a valid queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id and queue id")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?assignee_id=${u1.id}&queue_id=$q1")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1)))
    }

    Scenario("Filtering cases by a wrong assigneeId and a valid queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?assignee_id=_a_&queue_id=$q1")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be OK")
      result.status.intValue shouldEqual OK

      And("No cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

  }

  Feature("Get Cases by statuses") {

    Scenario("No matches") {

      storeCases(c1_updated, c2, c5)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?status=SUSPENDED")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

    Scenario("Filtering cases by single status") {

      storeCases(c1_updated, c2, c5)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?status=NEW")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2, c5)))
    }

    Scenario("Filtering cases by single pseudo status") {

      storeCases(c1_updated, c2, c6_live)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?status=LIVE")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c6_live)))
    }

    Scenario("Filtering cases by multiple statuses") {

      storeCases(c1_updated, c2, c5)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?status=NEW&status=CANCELLED")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1_updated, c2, c5)))
    }

    Scenario("Filtering cases by multiple pseudo statuses") {

      storeCases(c1_updated, c6_expired, c6_live)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?status=LIVE&status=EXPIRED")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c6_expired, c6_live)))
    }

    Scenario("Filtering cases by multiple statuses - comma separated") {

      storeCases(c1_updated, c2, c5)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?status=NEW,CANCELLED")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1_updated, c2, c5)))
    }

  }

  Feature("Get Cases by references") {

    Scenario("No matches") {

      storeCases(c2, c10)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?reference=a")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

    Scenario("Filtering cases by single reference") {

      storeCases(c2, c5, c10)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?reference=${c2.reference}")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2)))
    }

    Scenario("Filtering cases by multiple references") {

      storeCases(c2, c5, c10)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?reference=${c2.reference}&reference=${c5.reference}")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body).as[Paged[Case]].results.map(_.reference) shouldBe List(c2.reference, c5.reference)
    }

    Scenario("Filtering cases by multiple references - comma separated") {

      storeCases(c2, c5, c10)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?reference=${c2.reference},${c5.reference}")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body).as[Paged[Case]].results.map(_.reference) shouldBe List(c2.reference, c5.reference)
    }

  }

  Feature("Get Cases by keywords") {

    Scenario("No matches") {

      storeCases(c2, c10)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?keyword=PHONE")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

    Scenario("Filtering cases by single keyword") {

      storeCases(c2, c5, c10)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?keyword=MTB")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2, c10)))
    }

    Scenario("Filtering cases by multiple keywords") {

      storeCases(c2, c5, c10)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?keyword=MTB&keyword=HARDTAIL")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2)))
    }

    Scenario("Filtering cases by multiple keywords - comma separated") {

      storeCases(c2, c5, c10)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?keyword=MTB,HARDTAIL")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2)))
    }

  }

  Feature("Get Cases by trader name") {

    Scenario("Filtering cases by trader name") {

      storeCases(c1, c2, c5)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?case_source=John%20Lewis")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1, c2)))
    }

    Scenario("Case-insensitive search") {

      storeCases(c1)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?case_source=john%20Lewis")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1)))
    }

    Scenario("Search by substring") {

      storeCases(c1)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?case_source=Lewis")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1)))
    }

    Scenario("No matches") {

      storeCases(c1)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?case_source=Albert")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

  }

  Feature("Get Cases by Min Decision End Date") {

    Scenario("Filtering cases by Min Decision End Date") {

      storeCases(c1, c6_live)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?min_decision_end=1970-01-01T00:00:00Z")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c6_live)))
    }

    Scenario("Filtering cases by Min Decision End Date - filters decisions in the past") {

      storeCases(c1, c6_live)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?min_decision_end=3000-01-01T00:00:00Z")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

  }

  Feature("Get Cases by commodity code") {

    Scenario("filtering by non-existing commodity code") {

      storeCases(c1, c2, c5)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?commodity_code=66")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

    Scenario("filtering by existing commodity code") {

      storeCases(c1, c2, c5, c6_live)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?commodity_code=12345678")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2, c6_live)))
    }

    Scenario("Starts-with match") {

      storeCases(c1, c2, c5, c6_live)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?commodity_code=123")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2, c6_live)))
    }

    Scenario("Contains-match does not return any result") {

      storeCases(c2, c6_live)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?commodity_code=456")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

  }

  Feature("Get Cases by decision details") {

    Scenario("No matches") {

      storeCases(c1, c2, c5)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?decision_details=laptop")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

    Scenario("Filtering by existing good description") {

      storeCases(c1, c2, c7)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?decision_details=LAPTOP")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c7)))
    }

    Scenario("Filtering by method commercial denomination") {

      storeCases(c1, c2, c8)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?decision_details=laptop%20from%20Mexico")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c8)))
    }

    Scenario("Filtering by justification") {

      storeCases(c1, c2, c9)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?decision_details=this%20LLLLaptoppp")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c9)))
    }

    Scenario("Case-insensitive search") {

      storeCases(c1, c2, c7)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?decision_details=laptop")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c7)))
    }

    Scenario("Filtering by substring") {

      storeCases(c1, c2, c7, c8, c9)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?decision_details=laptop")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c7, c8, c9)))
    }

    Scenario("Filtering by goods description and expired case status") {

      storeCases(c11, c12, c13)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?decision_details=LAPTOP&status=EXPIRED")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c11)))
    }
  }

  Feature("Get Cases by EORI number") {

    val holderEori = "eori_01234"
    val agentEori  = "eori_98765"

    val agentDetails = createAgentDetails().copy(eoriDetails = createEORIDetails.copy(eori = agentEori))

    val holderApp = createBasicBTIApplication.copy(holder = createEORIDetails.copy(eori = holderEori), agent = None)
    val agentApp = createBTIApplicationWithAllFields()
      .copy(holder = createEORIDetails.copy(eori = holderEori), agent = Some(agentDetails))

    val agentCase  = adaptCaseInstantFormat(createCase(app = agentApp))
    val holderCase = adaptCaseInstantFormat(createCase(app = holderApp))

    Scenario("No matches") {
      storeCases(c1, c2)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?eori=333333")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

    Scenario("Filtering by agent EORI") {
      storeCases(c1, c2, agentCase, holderCase)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?eori=eori_98765")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(agentCase)))
    }

    Scenario("Filtering by applicant EORI") {
      storeCases(c1, c2, agentCase, holderCase)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?eori=eori_01234")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(agentCase, holderCase)))
    }

    Scenario("Case-insensitive search") {
      storeCases(c1, c2, agentCase, holderCase)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?eori=EORI_98765")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

    Scenario("Filtering by substring") {
      storeCases(c1, c2, agentCase, holderCase)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?eori=2345")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

  }

  Feature("Get Cases by application type") {

    Scenario("No matches") {

      storeCases(c1, c5)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?application_type=LIABILITY_ORDER")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

    Scenario("Filtering by existing application type") {

      storeCases(c1, c2, c7)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?application_type=BTI")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1, c7)))
    }

    Scenario("Case-insensitive search") {

      storeCases(c7)

      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?application_type=bti")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      result.status.intValue shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c7)))
    }
  }

  Feature("Get Cases sorted by commodity code") {

    val caseWithEmptyCommCode = adaptCaseInstantFormat(createCase().copy(decision = None))
    val caseY1 =
      adaptCaseInstantFormat(createCase().copy(decision = Some(createDecision(bindingCommodityCode = "777"))))
    val caseY2 =
      adaptCaseInstantFormat(createCase().copy(decision = Some(createDecision(bindingCommodityCode = "777"))))
    val caseZ =
      adaptCaseInstantFormat(createCase().copy(decision = Some(createDecision(bindingCommodityCode = "1111111111"))))

    Scenario("Sorting default - ascending order") {
      Given("There are few cases in the database")
      storeCases(caseY2, caseWithEmptyCommCode, caseY1, caseZ)

      When("I get all cases sorted by commodity code")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?sort_by=commodity-code")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 200")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseWithEmptyCommCode, caseZ, caseY2, caseY1)))
    }

    Scenario("Sorting in ascending order") {
      Given("There are few cases in the database")
      storeCases(caseY1, caseWithEmptyCommCode, caseY2, caseZ)

      When("I get all cases sorted by commodity code")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?sort_by=commodity-code&sort_direction=asc")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 200")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseWithEmptyCommCode, caseZ, caseY1, caseY2)))
    }

    Scenario("Sorting in descending order") {
      Given("There are few cases in the database")
      storeCases(caseZ, caseWithEmptyCommCode, caseY2, caseY1)

      When("I get all cases sorted by commodity code")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?sort_by=commodity-code&sort_direction=desc")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 200")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseY2, caseY1, caseZ, caseWithEmptyCommCode)))
    }

  }

  Feature("Get Case sorted by reference") {

    val case1 = adaptCaseInstantFormat(createCase().copy(reference = "1"))
    val case2 = adaptCaseInstantFormat(createCase().copy(reference = "2"))

    Scenario("Sorting default - ascending order") {
      Given("There are few cases in the database")
      storeCases(case1, case2)

      When("I get all cases sorted by reference")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?sort_by=reference")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 200")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(case1, case2)))
    }

    Scenario("Sorting in ascending order") {
      Given("There are few cases in the database")
      storeCases(case2, case1)

      When("I get all cases sorted by reference")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?sort_by=reference&sort_direction=asc")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 200")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(case1, case2)))
    }

    Scenario("Sorting in descending order") {
      Given("There are few cases in the database")
      storeCases(case1, case2)

      When("I get all cases sorted by reference")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?sort_by=reference&sort_direction=desc")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 200")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(case2, case1)))
    }

  }

  Feature("Get Cases sorted by days elapsed") {

    val oldCase = c1.copy(daysElapsed = 1)
    val newCase = c2.copy(daysElapsed = 0)

    Scenario("Sorting default - ascending order") {
      Given("There are few cases in the database")
      storeCases(oldCase, newCase)

      When("I get all cases sorted by elapsed days")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?sort_by=days-elapsed")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 200")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(newCase, oldCase)))
    }

    Scenario("Sorting in ascending order") {
      Given("There are few cases in the database")
      storeCases(oldCase, newCase)

      When("I get all cases sorted by elapsed days")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?sort_by=days-elapsed&sort_direction=asc")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 200")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(newCase, oldCase)))
    }

    Scenario("Sorting in descending order") {
      Given("There are few cases in the database")
      storeCases(oldCase, newCase)

      When("I get all cases sorted by elapsed days")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?sort_by=days-elapsed&sort_direction=desc")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 200")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(oldCase, newCase)))
    }

  }

  Feature("Get Cases with Pagination") {

    Scenario("Paginates with 'page_size' and 'page'") {

      storeCases(c1, c2)

      val responseFuture1 = httpClient
        .url(s"$serviceUrl/cases?page_size=1&page=1")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result1 = Await.result(responseFuture1, Duration(1000L, "ms"))

      result1.status.intValue shouldEqual OK
      Json.parse(result1.body) shouldBe Json.toJson(
        Paged(results = Seq(c1), pageIndex = 1, pageSize = 1, resultCount = 2)
      )

      val responseFuture2 = httpClient
        .url(s"$serviceUrl/cases?page_size=1&page=2")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result2 = Await.result(responseFuture2, Duration(1000L, "ms"))

      result2.status.intValue shouldEqual OK
      Json.parse(result2.body) shouldBe Json.toJson(
        Paged(results = Seq(c2), pageIndex = 2, pageSize = 1, resultCount = 2)
      )
    }

  }

  Feature("Get Cases sorted by case created date") {

    val caseD0 = adaptCaseInstantFormat(createCase().copy(createdDate = Instant.now()))
    val caseD1 = adaptCaseInstantFormat(createCase().copy(createdDate = Instant.now().minus(1, ChronoUnit.DAYS)))
    val caseD2 = adaptCaseInstantFormat(createCase().copy(createdDate = Instant.now().minus(2, ChronoUnit.DAYS)))
    val caseD3 = adaptCaseInstantFormat(createCase().copy(createdDate = Instant.now().minus(3, ChronoUnit.DAYS)))

    Scenario("Sorting default - ascending order") {
      Given("There are few cases in the database")
      storeCases(caseD0, caseD1, caseD2, caseD3)

      When("I get all cases sorted by created date")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?sort_by=created-date")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 200")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseD3, caseD2, caseD1, caseD0)))
    }

    Scenario("Sorting in ascending order") {
      Given("There are few cases in the database")
      storeCases(caseD0, caseD1, caseD2, caseD3)

      When("I get all cases sorted by created date")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?sort_by=created-date&sort_direction=asc")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 200")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseD3, caseD2, caseD1, caseD0)))
    }

    Scenario("Sorting in descending order") {
      Given("There are few cases in the database")
      storeCases(caseD0, caseD1, caseD2, caseD3)

      When("I get all cases sorted by created date")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?sort_by=created-date&sort_direction=desc")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 200")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseD0, caseD1, caseD2, caseD3)))
    }

  }

  Feature("Get Cases sorted by case decision effective start date") {

    val caseD0 = adaptCaseInstantFormat(
      createCase().copy(decision = Some(createDecision(effectiveStartDate = Some(Instant.now()))))
    )
    val caseD1 = adaptCaseInstantFormat(
      createCase()
        .copy(decision = Some(createDecision(effectiveStartDate = Some(Instant.now().minus(1, ChronoUnit.DAYS)))))
    )
    val caseD2 = adaptCaseInstantFormat(
      createCase()
        .copy(decision = Some(createDecision(effectiveStartDate = Some(Instant.now().minus(2, ChronoUnit.DAYS)))))
    )
    val caseD3 = adaptCaseInstantFormat(
      createCase()
        .copy(decision = Some(createDecision(effectiveStartDate = Some(Instant.now().minus(3, ChronoUnit.DAYS)))))
    )

    Scenario("Sorting default - ascending order") {
      Given("There are few cases in the database")
      storeCases(caseD0, caseD3, caseD2, caseD1)

      When("I get all cases sorted by created date")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?sort_by=decision-start-date")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 200")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseD3, caseD2, caseD1, caseD0)))
    }

    Scenario("Sorting in ascending order") {
      Given("There are few cases in the database")
      storeCases(caseD0, caseD1, caseD2, caseD3)

      When("I get all cases sorted by decision effective start date")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?sort_by=decision-start-date&sort_direction=asc")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 200")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseD3, caseD2, caseD1, caseD0)))
    }

    Scenario("Sorting in descending order") {
      Given("There are few cases in the database")
      storeCases(caseD0, caseD3, caseD2, caseD1)

      When("I get all cases sorted by decision effective start date")
      val responseFuture = httpClient
        .url(s"$serviceUrl/cases?sort_by=decision-start-date&sort_direction=desc")
        .withHttpHeaders(apiTokenKey -> appConfig.authorization)
        .get()
      val result = Await.result(responseFuture, Duration(1000L, "ms"))

      Then("The response code should be 200")
      result.status.intValue shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseD0, caseD1, caseD2, caseD3)))
    }

  }

  Feature("Check Cases Serialization") {
    val contact = Contact("Charles", "test@test.com", None)

    val address = Address(
      buildingAndStreet = "Evergreen 305",
      townOrCity = "Springfield",
      county = Option("TX"),
      postCode = Option("E123123")
    )

    val message = Message("Charles", Instant.EPOCH, "running out of fuel")

    val app = CorrespondenceApplication(
      correspondenceStarter = Option("starter"),
      agentName = Option("Sean"),
      address = address,
      contact = contact,
      fax = Option("fax"),
      summary = "no comments",
      detailedDescription = "same",
      relatedBTIReference = Option("n.a"),
      relatedBTIReferences = List("n.a"),
      sampleToBeProvided = true,
      sampleToBeReturned = false,
      messagesLogged = List(message)
    )

    Scenario("on CorrespondenceApplication fields") {
      Given("CorrespondenceApplication")
      val json         = Json.toJson(app)
      val deserialized = json.as[CorrespondenceApplication]

      deserialized.shouldBe(app)
    }

    Scenario("on Case with default values") {
      val caseWithDefaults: Case = Case(
        reference = "ref1",
        status = CaseStatus.NEW,
        application = app
      )

      val json         = Json.toJson(caseWithDefaults)
      val deserialized = json.as[Case]

      deserialized.shouldBe(caseWithDefaults)
      deserialized.attachments.shouldBe(Seq.empty)
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
            dateOfReceipt = liabilityApp.dateOfReceipt.map(_.truncatedTo(ChronoUnit.MILLIS)),
            repaymentClaim = liabilityApp.repaymentClaim.map { claim =>
              claim.copy(dateForRepayment = claim.dateForRepayment.map(_.truncatedTo(ChronoUnit.MILLIS)))
            }
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
      },
      attachments = _case.attachments.map { attch =>
        attch.copy(timestamp = attch.timestamp.truncatedTo(ChronoUnit.MILLIS))
      }
    )
  }
}
