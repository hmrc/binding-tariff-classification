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

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant}

import play.api.http.ContentTypes.JSON
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.HttpVerbs
import play.api.http.Status._
import play.api.libs.json.Json
import scalaj.http.{Http, HttpResponse}
import uk.gov.hmrc.bindingtariffclassification.model.ImportExport.EXPORT
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import util.CaseData._
import util.Matchers.roughlyBe

class CaseSpec extends BaseFeatureSpec {

  override lazy val port = 14681
  protected val serviceUrl = s"http://localhost:$port"

  private val clock = Clock.systemUTC()
  private val q1 = "queue1"
  private val u1 = Operator("user1")
  private val c0 = createNewCase(app = createBasicBTIApplication)
  private val c1 = createCase(app = createBasicBTIApplication, queue = Some(q1), assignee = Some(u1))
  private val status = CaseStatus.CANCELLED
  private val c1_updated = c1.copy(status = status)
  private val c2 = createCase(r = "case_ref_2", app = createLiabilityOrder,
    decision = Some(createDecision()),
    attachments = Seq(createAttachment,createAttachmentWithOperator),
    keywords = Set("BIKE", "MTB", "HARDTAIL"))
  private val c3 = createNewCaseWithExtraFields()
  private val c4 = createNewCase(app = createBTIApplicationWithAllFields)
  private val c5 = createCase(r = "case_ref_5", app = createBasicBTIApplication.copy(holder = eORIDetailForNintedo))
  private val c6_live = createCase(status = CaseStatus.COMPLETED, decision = Some(createDecision(effectiveEndDate = Some(Instant.now(clock).plusSeconds(3600 * 24)))))
  private val c6_expired = createCase(status = CaseStatus.COMPLETED, decision = Some(createDecision(effectiveEndDate = Some(Instant.now(clock).minusSeconds(3600 * 24)))))
  private val c7 = createCase(decision = Some(createDecision(goodsDescription = "LAPTOP")))
  private val c8 = createCase(decision = Some(createDecision(methodCommercialDenomination = Some("laptop from Mexico"))))
  private val c9 = createCase(decision = Some(createDecision(justification = "this LLLLaptoppp")))
  private val c10 = createCase(keywords = Set("MTB", "BICYCLE"))
  private val c11 = createCase(decision = Some(createDecision(
      goodsDescription = "LAPTOP",
      effectiveStartDate = Some(Instant.now().minus(3, ChronoUnit.DAYS)),
      effectiveEndDate = Some(Instant.now().minus(1, ChronoUnit.DAYS)))
    ),
    status = CaseStatus.COMPLETED)
  private val c12 = createCase(decision = Some(createDecision(
    goodsDescription = "SPANNER",
    effectiveStartDate = Some(Instant.now().minus(3, ChronoUnit.DAYS)),
    effectiveEndDate = Some(Instant.now().minus(1, ChronoUnit.DAYS)))
  ),
    status = CaseStatus.COMPLETED)
  private val c13 = createCase(decision = Some(createDecision(
    goodsDescription = "LAPTOP",
    effectiveStartDate = Some(Instant.now()),
    effectiveEndDate = Some(Instant.now().plus(1, ChronoUnit.DAYS)))
  ),
    status = CaseStatus.COMPLETED)
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
        .header(apiTokenKey, appConfig.authorization)
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
        .header(apiTokenKey, appConfig.authorization)
        .header(CONTENT_TYPE, JSON)
        .postData(c0Json.toString()).asString

      Then("The response code should be created")
      result.code shouldEqual CREATED

      And("The case is returned in the JSON response")
      val responseCase = Json.parse(result.body).as[Case]
      responseCase.reference shouldBe "504400001"
      responseCase.status shouldBe CaseStatus.NEW
      responseCase.application.asBTI.importOrExport shouldBe None
    }

    scenario("Extra fields are ignored when creating a case") {
      When("I create a new case with extra fields")
      val result: HttpResponse[String] = Http(s"$serviceUrl/cases")
        .header(apiTokenKey, appConfig.authorization)
        .header(CONTENT_TYPE, JSON)
        .postData(c3Json.toString()).asString

      Then("The response code should be created")
      result.code shouldEqual CREATED

      And("The case is returned in the JSON response")
      val responseCase = Json.parse(result.body).as[Case]
      responseCase.reference shouldBe "504400001"
      responseCase.status shouldBe CaseStatus.NEW
      responseCase.createdDate should roughlyBe(Instant.now())
      responseCase.assignee shouldBe None
      responseCase.queueId shouldBe None
      responseCase.decision shouldBe None
    }

    scenario("Create a new case with all fields") {

      When("I create a new case")
      val result: HttpResponse[String] = Http(s"$serviceUrl/cases")
        .header(apiTokenKey, appConfig.authorization)
        .header(CONTENT_TYPE, JSON)
        .postData(c4Json.toString()).asString

      Then("The response code should be created")
      result.code shouldEqual CREATED

      And("The case is returned in the JSON response")
      val responseCase = Json.parse(result.body).as[Case]
      responseCase.reference shouldBe "504400001"
      responseCase.status shouldBe CaseStatus.NEW
      responseCase.application.asBTI.importOrExport shouldBe Some(EXPORT)
    }

  }


  feature("Update Case") {

    scenario("Update an non-existing case") {

      When("I update a non-existing case")
      val result = Http(s"$serviceUrl/cases/${c1.reference}")
        .header(apiTokenKey, appConfig.authorization)
        .header(CONTENT_TYPE, JSON)
        .put(c1Json.toString()).asString

      Then("The response code should be NOT FOUND")
      result.code shouldEqual NOT_FOUND
    }

    scenario("Update an existing case") {

      Given("There is a case in the database")
      storeCases(c1)

      When("I update an existing case")
      val result = Http(s"$serviceUrl/cases/${c1.reference}")
        .header(apiTokenKey, appConfig.authorization)
        .header(CONTENT_TYPE, JSON)
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
      val result = Http(s"$serviceUrl/cases/${c1.reference}")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The case is returned in the JSON response")
      Json.parse(result.body) shouldBe c1Json
    }

    scenario("Get a non-existing case") {

      When("I get a case")
      val result = Http(s"$serviceUrl/cases/${c1.reference}")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be NOT FOUND")
      result.code shouldEqual NOT_FOUND
    }

  }


  feature("Get All Cases") {

    scenario("Get all cases") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get all cases")
      val result = Http(s"$serviceUrl/cases")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1, c2)))
    }

    scenario("Get no cases") {

      Given("There are no cases in the database")

      When("I get all cases")
      val result = Http(s"$serviceUrl/cases")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("No cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

  }


  feature("Get Cases by Queue Id") {

    scenario("Filtering cases that have undefined queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by queue id")
      val result = Http(s"$serviceUrl/cases?queue_id=none")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2)))
    }

    scenario("Filtering cases that have defined queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by queue id")
      val result = Http(s"$serviceUrl/cases?queue_id=some")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1)))
    }

    scenario("Filtering cases by a valid queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by queue id")
      val result = Http(s"$serviceUrl/cases?queue_id=$q1")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1)))
    }

    scenario("Filtering cases by a wrong queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by queue id")
      val result = Http(s"$serviceUrl/cases?queue_id=wrong")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("No cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

  }


  feature("Get Cases by Assignee Id") {

    scenario("Filtering cases that have undefined assigneeId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id")
      val result = Http(s"$serviceUrl/cases?assignee_id=none")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2)))
    }

    scenario("Filtering cases that have defined assigneeId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id")
      val result = Http(s"$serviceUrl/cases?assignee_id=some")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1)))
    }

    scenario("Filtering cases by a valid assigneeId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id")
      val result = Http(s"$serviceUrl/cases?assignee_id=${u1.id}")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1)))
    }

    scenario("Filtering cases by a wrong assigneeId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id")
      val result = Http(s"$serviceUrl/cases?assignee_id=wrong")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("No cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

  }


  feature("Get Cases by Assignee Id and Queue Id") {

    scenario("Filtering cases that have undefined assigneeId and undefined queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id and queue id")
      val result = Http(s"$serviceUrl/cases?assignee_id=none&queue_id=none")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2)))
    }

    scenario("Filtering cases by a valid assigneeId and a valid queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id and queue id")
      val result = Http(s"$serviceUrl/cases?assignee_id=${u1.id}&queue_id=$q1")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1)))
    }

    scenario("Filtering cases by a wrong assigneeId and a valid queueId") {

      Given("There are few cases in the database")
      storeCases(c1, c2)

      When("I get cases by assignee id")
      val result = Http(s"$serviceUrl/cases?assignee_id=_a_&queue_id=$q1")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be OK")
      result.code shouldEqual OK

      And("No cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

  }


  feature("Get Cases by statuses") {

    scenario("No matches") {

      storeCases(c1_updated, c2, c5)

      val result = Http(s"$serviceUrl/cases?status=SUSPENDED")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

    scenario("Filtering cases by single status") {

      storeCases(c1_updated, c2, c5)

      val result = Http(s"$serviceUrl/cases?status=NEW")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2,c5)))
    }

    scenario("Filtering cases by single pseudo status") {

      storeCases(c1_updated, c2, c6_live)

      val result = Http(s"$serviceUrl/cases?status=LIVE")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c6_live)))
    }

    scenario("Filtering cases by multiple statuses") {

      storeCases(c1_updated, c2, c5)

      val result = Http(s"$serviceUrl/cases?status=NEW&status=CANCELLED")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1_updated,c2,c5)))
    }

    scenario("Filtering cases by multiple pseudo statuses") {

      storeCases(c1_updated, c6_expired, c6_live)

      val result = Http(s"$serviceUrl/cases?status=LIVE&status=EXPIRED")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c6_expired, c6_live)))
    }

    scenario("Filtering cases by multiple statuses - comma separated") {

      storeCases(c1_updated, c2, c5)

      val result = Http(s"$serviceUrl/cases?status=NEW,CANCELLED")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1_updated,c2,c5)))
    }

  }

  feature("Get Cases by references") {

    scenario("No matches") {

      storeCases(c2, c10)

      val result = Http(s"$serviceUrl/cases?reference=a")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

    scenario("Filtering cases by single reference") {

      storeCases(c2, c5, c10)

      val result = Http(s"$serviceUrl/cases?reference=${c2.reference}")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2)))
    }

    scenario("Filtering cases by multiple references") {

      storeCases(c2, c5, c10)

      val result = Http(s"$serviceUrl/cases?reference=${c2.reference}&reference=${c5.reference}")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body).as[Paged[Case]].results.map(_.reference) should contain only (c2.reference,c5.reference)
    }

    scenario("Filtering cases by multiple references - comma separated") {

      storeCases(c2, c5, c10)

      val result = Http(s"$serviceUrl/cases?reference=${c2.reference},${c5.reference}")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body).as[Paged[Case]].results.map(_.reference) should contain only (c2.reference,c5.reference)
    }

  }


  feature("Get Cases by keywords") {

    scenario("No matches") {

      storeCases(c2, c10)

      val result = Http(s"$serviceUrl/cases?keyword=PHONE")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

    scenario("Filtering cases by single keyword") {

      storeCases(c2, c5, c10)

      val result = Http(s"$serviceUrl/cases?keyword=MTB")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2, c10)))
    }

    scenario("Filtering cases by multiple keywords") {

      storeCases(c2, c5, c10)

      val result = Http(s"$serviceUrl/cases?keyword=MTB&keyword=HARDTAIL")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2)))
    }

    scenario("Filtering cases by multiple keywords - comma separated") {

      storeCases(c2, c5, c10)

      val result = Http(s"$serviceUrl/cases?keyword=MTB,HARDTAIL")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2)))
    }

  }


  feature("Get Cases by trader name") {

    scenario("Filtering cases by trader name") {

      storeCases(c1, c2, c5)

      val result = Http(s"$serviceUrl/cases?trader_name=John%20Lewis")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1,c2)))
    }

    scenario("Case-insensitive search") {

      storeCases(c1)

      val result = Http(s"$serviceUrl/cases?trader_name=john%20Lewis")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1)))
    }

    scenario("Search by substring") {

      storeCases(c1)

      val result = Http(s"$serviceUrl/cases?trader_name=Lewis")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1)))
    }

    scenario("No matches") {

      storeCases(c1)

      val result = Http(s"$serviceUrl/cases?trader_name=Albert")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

  }


  feature("Get Cases by Min Decision End Date") {

    scenario("Filtering cases by Min Decision End Date") {

      storeCases(c1, c6_live)

      val result = Http(s"$serviceUrl/cases?min_decision_end=1970-01-01T00:00:00Z")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c6_live)))
    }

    scenario("Filtering cases by Min Decision End Date - filters decisions in the past") {

      storeCases(c1, c6_live)

      val result = Http(s"$serviceUrl/cases?min_decision_end=3000-01-01T00:00:00Z")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

  }


  feature("Get Cases by commodity code") {

    scenario("filtering by non-existing commodity code") {

      storeCases(c1, c2, c5)

      val result = Http(s"$serviceUrl/cases?commodity_code=66")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

    scenario("filtering by existing commodity code") {

      storeCases(c1, c2, c5, c6_live)

      val result = Http(s"$serviceUrl/cases?commodity_code=12345678")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2,c6_live)))
    }

    scenario("Starts-with match") {

      storeCases(c1, c2, c5, c6_live)

      val result = Http(s"$serviceUrl/cases?commodity_code=123")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c2,c6_live)))
    }

    scenario("Contains-match does not return any result") {

      storeCases(c2, c6_live)

      val result = Http(s"$serviceUrl/cases?commodity_code=456")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

  }


  feature("Get Cases by decision details") {

    scenario("No matches") {

      storeCases(c1, c2, c5)

      val result = Http(s"$serviceUrl/cases?decision_details=laptop")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

    scenario("Filtering by existing good description") {

      storeCases(c1, c2, c7)

      val result = Http(s"$serviceUrl/cases?decision_details=LAPTOP")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c7)))
    }

    scenario("Filtering by method commercial denomination") {

      storeCases(c1, c2, c8)

      val result = Http(s"$serviceUrl/cases?decision_details=laptop%20from%20Mexico")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c8)))
    }

    scenario("Filtering by justification") {

      storeCases(c1, c2, c9)

      val result = Http(s"$serviceUrl/cases?decision_details=this%20LLLLaptoppp")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c9)))
    }

    scenario("Case-insensitive search") {

      storeCases(c1, c2, c7)

      val result = Http(s"$serviceUrl/cases?decision_details=laptop")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c7)))
    }

    scenario("Filtering by substring") {

      storeCases(c1, c2, c7, c8, c9)

      val result = Http(s"$serviceUrl/cases?decision_details=laptop")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c7, c8, c9)))
    }

    scenario("Filtering by goods description and expired case status") {

      storeCases(c11, c12, c13)

      val result = Http(s"$serviceUrl/cases?decision_details=LAPTOP&status=EXPIRED")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c11)))
    }
  }


  feature("Get Cases by EORI number") {

    val holderEori = "eori_01234"
    val agentEori = "eori_98765"

    val agentDetails = createAgentDetails.copy(eoriDetails = createEORIDetails.copy(eori = agentEori))

    val holderApp = createBasicBTIApplication.copy(holder = createEORIDetails.copy(eori = holderEori), agent = None)
    val agentApp = createBTIApplicationWithAllFields.copy(holder = createEORIDetails.copy(eori = holderEori), agent = Some(agentDetails))

    val agentCase = createCase(app = agentApp)
    val holderCase = createCase(app = holderApp)

    scenario("No matches") {
      storeCases(c1, c2)

      val result = Http(s"$serviceUrl/cases?eori=333333")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

    scenario("Filtering by agent EORI") {
      storeCases(c1, c2, agentCase, holderCase)

      val result = Http(s"$serviceUrl/cases?eori=eori_98765")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(agentCase)))
    }

    scenario("Filtering by applicant EORI") {
      storeCases(c1, c2, agentCase, holderCase)

      val result = Http(s"$serviceUrl/cases?eori=eori_01234")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(agentCase, holderCase)))
    }

    scenario("Case-insensitive search") {
      storeCases(c1, c2, agentCase, holderCase)

      val result = Http(s"$serviceUrl/cases?eori=EORI_98765")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

    scenario("Filtering by substring") {
      storeCases(c1, c2, agentCase, holderCase)

      val result = Http(s"$serviceUrl/cases?eori=2345")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

  }


  feature("Get Cases by BTI") {

    scenario("No matches") {

      storeCases(c1, c5)

      val result = Http(s"$serviceUrl/cases?application_type=LIABILITY_ORDER")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged.empty[Case])
    }

    scenario("Filtering by existing application type") {

      storeCases(c1, c2, c7)

      val result = Http(s"$serviceUrl/cases?application_type=BTI")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c1, c7)))
    }

    scenario("Case-insensitive search") {

      storeCases(c7)

      val result = Http(s"$serviceUrl/cases?application_type=bti")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result.code shouldEqual OK
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(c7)))
    }
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
      val result = Http(s"$serviceUrl/cases?sort_by=commodity-code")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseWithEmptyCommCode, caseZ, caseY2, caseY1)))
    }

    scenario("Sorting in ascending order") {
      Given("There are few cases in the database")
      storeCases(caseY1, caseWithEmptyCommCode, caseY2, caseZ)

      When("I get all cases sorted by commodity code")
      val result = Http(s"$serviceUrl/cases?sort_by=commodity-code&sort_direction=asc")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseWithEmptyCommCode, caseZ, caseY1, caseY2)))
    }

    scenario("Sorting in descending order") {
      Given("There are few cases in the database")
      storeCases(caseZ, caseWithEmptyCommCode, caseY1, caseY2)

      When("I get all cases sorted by commodity code")
      val result = Http(s"$serviceUrl/cases?sort_by=commodity-code&sort_direction=desc")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseY2, caseY1, caseZ, caseWithEmptyCommCode)))
    }

  }

  feature("Get Case sorted by reference") {

    val case1 = createCase().copy(reference = "1")
    val case2 = createCase().copy(reference = "2")

    scenario("Sorting default - ascending order") {
      Given("There are few cases in the database")
      storeCases(case1, case2)

      When("I get all cases sorted by reference")
      val result = Http(s"$serviceUrl/cases?sort_by=reference")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(case1, case2)))
    }

    scenario("Sorting in ascending order") {
      Given("There are few cases in the database")
      storeCases(case2, case1)

      When("I get all cases sorted by reference")
      val result = Http(s"$serviceUrl/cases?sort_by=reference&sort_direction=asc")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(case1, case2)))
    }

    scenario("Sorting in descending order") {
      Given("There are few cases in the database")
      storeCases(case1, case2)

      When("I get all cases sorted by reference")
      val result = Http(s"$serviceUrl/cases?sort_by=reference&sort_direction=desc")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(case2, case1)))
    }

  }


  feature("Get Cases sorted by days elapsed") {

    val oldCase = c1.copy(daysElapsed = 1)
    val newCase = c2.copy(daysElapsed = 0)

    scenario("Sorting default - ascending order") {
      Given("There are few cases in the database")
      storeCases(oldCase, newCase)

      When("I get all cases sorted by elapsed days")
      val result = Http(s"$serviceUrl/cases?sort_by=days-elapsed")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(newCase, oldCase)))
    }

    scenario("Sorting in ascending order") {
      Given("There are few cases in the database")
      storeCases(oldCase, newCase)

      When("I get all cases sorted by elapsed days")
      val result = Http(s"$serviceUrl/cases?sort_by=days-elapsed&sort_direction=asc")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(newCase, oldCase)))
    }

    scenario("Sorting in descending order") {
      Given("There are few cases in the database")
      storeCases(oldCase, newCase)

      When("I get all cases sorted by elapsed days")
      val result = Http(s"$serviceUrl/cases?sort_by=days-elapsed&sort_direction=desc")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(oldCase, newCase)))
    }

  }


  feature("Get Cases with Pagination") {

    scenario("Paginates with 'page_size' and 'page'") {

      storeCases(c1, c2)

      val result1 = Http(s"$serviceUrl/cases?page_size=1&page=1")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result1.code shouldEqual OK
      Json.parse(result1.body) shouldBe Json.toJson(Paged(results = Seq(c1), pageIndex = 1, pageSize = 1, resultCount = 2))

      val result2 = Http(s"$serviceUrl/cases?page_size=1&page=2")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      result2.code shouldEqual OK
      Json.parse(result2.body) shouldBe Json.toJson(Paged(results = Seq(c2), pageIndex = 2, pageSize = 1, resultCount = 2))
    }

  }

  feature("Get Cases sorted by case created date") {

    val caseD0 = createCase().copy(createdDate = Instant.now())
    val caseD1 = createCase().copy(createdDate = Instant.now().minus(1, ChronoUnit.DAYS))
    val caseD2 = createCase().copy(createdDate = Instant.now().minus(2, ChronoUnit.DAYS))
    val caseD3 = createCase().copy(createdDate = Instant.now().minus(3, ChronoUnit.DAYS))


    scenario("Sorting default - ascending order") {
      Given("There are few cases in the database")
      storeCases(caseD0, caseD1, caseD2, caseD3)

      When("I get all cases sorted by created date")
      val result = Http(s"$serviceUrl/cases?sort_by=created-date")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseD3, caseD2, caseD1, caseD0)))
    }

    scenario("Sorting in ascending order") {
      Given("There are few cases in the database")
      storeCases(caseD0, caseD1, caseD2, caseD3)

      When("I get all cases sorted by created date")
      val result = Http(s"$serviceUrl/cases?sort_by=created-date&sort_direction=asc")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseD3, caseD2, caseD1, caseD0)))
    }

    scenario("Sorting in descending order") {
      Given("There are few cases in the database")
      storeCases(caseD0, caseD1, caseD2, caseD3)

      When("I get all cases sorted by created date")
      val result = Http(s"$serviceUrl/cases?sort_by=created-date&sort_direction=desc")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseD0, caseD1, caseD2, caseD3)))
    }

  }

  feature("Get Cases sorted by case decision effective start date") {

    val caseD0 = createCase().copy(decision = Some(createDecision(effectiveStartDate = Some(Instant.now()))))
    val caseD1 = createCase().copy(decision = Some(createDecision(effectiveStartDate = Some(Instant.now().minus(1, ChronoUnit.DAYS)))))
    val caseD2 = createCase().copy(decision = Some(createDecision(effectiveStartDate = Some(Instant.now().minus(2, ChronoUnit.DAYS)))))
    val caseD3 = createCase().copy(decision = Some(createDecision(effectiveStartDate = Some(Instant.now().minus(3, ChronoUnit.DAYS)))))


    scenario("Sorting default - ascending order") {
      Given("There are few cases in the database")
      storeCases(caseD0, caseD3, caseD2, caseD1)

      When("I get all cases sorted by created date")
      val result = Http(s"$serviceUrl/cases?sort_by=decision-start-date")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseD3, caseD2, caseD1, caseD0)))
    }

    scenario("Sorting in ascending order") {
      Given("There are few cases in the database")
      storeCases(caseD0, caseD1, caseD2, caseD3)

      When("I get all cases sorted by decision effective start date")
      val result = Http(s"$serviceUrl/cases?sort_by=decision-start-date&sort_direction=asc")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseD3, caseD2, caseD1, caseD0)))
    }

    scenario("Sorting in descending order") {
      Given("There are few cases in the database")
      storeCases(caseD0, caseD3, caseD2, caseD1)

      When("I get all cases sorted by decision effective start date")
      val result = Http(s"$serviceUrl/cases?sort_by=decision-start-date&sort_direction=desc")
        .header(apiTokenKey, appConfig.authorization)
        .asString

      Then("The response code should be 200")
      result.code shouldEqual OK

      And("The expected cases are returned in the JSON response")
      Json.parse(result.body) shouldBe Json.toJson(Paged(Seq(caseD0, caseD1, caseD2, caseD3)))
    }

  }

}
