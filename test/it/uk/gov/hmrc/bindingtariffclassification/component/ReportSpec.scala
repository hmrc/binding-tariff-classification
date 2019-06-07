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

import java.time.{Instant, LocalDateTime, ZoneOffset}

import play.api.Logger
import play.api.http.HttpVerbs
import play.api.http.Status.OK
import play.api.libs.json.{Json, Reads}
import scalaj.http.{Http, HttpResponse}
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.reporting.ReportResult
import util.Cases._
import util.EventData._

class ReportSpec extends BaseFeatureSpec {

  override lazy val port = 14682
  protected val serviceUrl = s"http://localhost:$port"

  feature("Report") {

    scenario("Generate a Report on Active Days Elapsed Grouping by Queue") {
      Given("There are some documents in the collection")
      givenThereIs(aCase(withoutQueue(), withActiveDaysElapsed(0)))
      givenThereIs(aCase(withQueue("queue-1"), withActiveDaysElapsed(1)))
      givenThereIs(aCase(withQueue("queue-1"), withActiveDaysElapsed(2)))

      When("I request the report")
      val result = whenIGET("report",
        withParams(
          "report_field" -> "active-days-elapsed",
          "report_group" -> "queue-id"
        )
      )

      Then("The response code should be 200")
      result.code shouldBe OK

      And("The response body contains the report")
      thenTheJsonBodyOf[Seq[ReportResult]](result).get.toSet shouldBe Set(ReportResult(None, Seq(0)), ReportResult("queue-1", Seq(1, 2)))
    }

    scenario("Generate a Report on Referred Days Elapsed Grouping by Queue") {
      Given("There are some documents in the collection")
      givenThereIs(aCase(withoutQueue(), withReferredDaysElapsed(0)))
      givenThereIs(aCase(withQueue("queue-1"), withReferredDaysElapsed(1)))
      givenThereIs(aCase(withQueue("queue-1"), withReferredDaysElapsed(2)))

      When("I request the report")
      val result = whenIGET("report",
        withParams(
          "report_field" -> "referred-days-elapsed",
          "report_group" -> "queue-id"
        )
      )

      Then("The response code should be 200")
      result.code shouldBe OK

      And("The response body contains the report")
      thenTheJsonBodyOf[Seq[ReportResult]](result).get.toSet shouldBe Set(ReportResult(None, Seq(0)), ReportResult("queue-1", Seq(1, 2)))
    }

    scenario("Generate a Report filtering by decision date") {
      Given("There are some documents in the collection")
      givenThereIs(aCase(withQueue("queue-1"), withActiveDaysElapsed(1), withDecision(effectiveStartDate = Some(date("2019-01-01T00:00:00")))))
      givenThereIs(aCase(withQueue("queue-1"), withActiveDaysElapsed(2), withDecision(effectiveStartDate = Some(date("2019-02-01T00:00:00")))))
      givenThereIs(aCase(withQueue("queue-1"), withActiveDaysElapsed(3), withDecision(effectiveStartDate = Some(date("2019-03-01T00:00:00")))))

      When("I request the report")
      val result = whenIGET("report",
        withParams(
          "report_field" -> "active-days-elapsed",
          "report_group" -> "queue-id",
          "min_decision_start" -> "2019-01-15T00:00:00Z",
          "max_decision_start" -> "2019-02-15T00:00:00Z"
        )
      )

      Then("The response code should be 200")
      result.code shouldBe OK

      And("The response body contains the report")
      thenTheJsonBodyOf[Seq[ReportResult]](result) shouldBe Some(Seq(ReportResult("queue-1", Seq(2))))
    }

    scenario("Generate a Report filtering by referral date") {
      Given("There are some documents in the collection")
      givenThereIs(aCase(withReference("ref1"), withQueue("queue-1"), withActiveDaysElapsed(1)))
      givenThereIs(anEvent(withCaseReference("ref1"), withStatusChange(CaseStatus.OPEN, CaseStatus.REFERRED), withTimestamp("2019-02-01T00:00:00")))

      givenThereIs(aCase(withReference("ref2"), withQueue("queue-1"), withActiveDaysElapsed(2)))
      givenThereIs(anEvent(withCaseReference("ref1"), withStatusChange(CaseStatus.REFERRED, CaseStatus.OPEN), withTimestamp("2019-03-01T00:00:00")))

      When("I request the report")
      val result = whenIGET("report",
        withParams(
          "report_field" -> "active-days-elapsed",
          "report_group" -> "queue-id",
          "min_referral_date" -> "2019-01-15T00:00:00Z",
          "max_referral_date" -> "2019-02-15T00:00:00Z"
        )
      )

      Then("The response code should be 200")
      result.code shouldBe OK

      And("The response body contains the report")
      thenTheJsonBodyOf[Seq[ReportResult]](result) shouldBe Some(Seq(ReportResult("queue-1", Seq(1))))
    }

    scenario("Generate a Report filtering by reference") {
      Given("There are some documents in the collection")
      givenThereIs(aCase(withReference("1"), withQueue("queue-1"), withActiveDaysElapsed(1)))
      givenThereIs(aCase(withReference("2"), withQueue("queue-1"), withActiveDaysElapsed(2)))
      givenThereIs(aCase(withReference("3"), withQueue("queue-1"), withActiveDaysElapsed(3)))

      When("I request the report")
      val result = whenIGET("report",
        withParams(
          "report_field" -> "active-days-elapsed",
          "report_group" -> "queue-id",
          "reference" -> "1,2"
        )
      )

      Then("The response code should be 200")
      result.code shouldBe OK

      And("The response body contains the report")
      thenTheJsonBodyOf[Seq[ReportResult]](result) shouldBe Some(Seq(ReportResult("queue-1", Seq(1, 2))))
    }

    scenario("Generate a Report filtering by application type") {
      Given("There are some documents in the collection")
      givenThereIs(aCase(withQueue("queue-1"), withActiveDaysElapsed(1), withBTIDetails()))
      givenThereIs(aCase(withQueue("queue-1"), withActiveDaysElapsed(2), withLiabilityDetails()))

      When("I request the report")
      val result = whenIGET("report",
        withParams(
          "report_field" -> "active-days-elapsed",
          "report_group" -> "queue-id",
          "application_type" -> "BTI"
        )
      )

      Then("The response code should be 200")
      result.code shouldBe OK

      And("The response body contains the report")
      thenTheJsonBodyOf[Seq[ReportResult]](result) shouldBe Some(Seq(ReportResult("queue-1", Seq(1))))
    }

  }

  private def date(d: String): Instant = LocalDateTime.parse(d).atOffset(ZoneOffset.UTC).toInstant

  private def withParams(params: (String, String)*): Map[String, String] = Map(params: _*)

  private def whenIGET(path: String, params: Map[String, String]): HttpResponse[String] = {
    val query = if (params.isEmpty) "" else "?" + params.map(p => s"${p._1}=${p._2}").mkString("&")
    val url = s"$serviceUrl/$path$query"
    Logger.info(s"GET-ing [$url]")
    Http(url)
      .header(apiTokenKey, appConfig.authorization)
      .method(HttpVerbs.GET)
      .asString
  }

  private def thenTheJsonBodyOf[T](response: HttpResponse[String])(implicit rds: Reads[T]): Option[T] = Json.fromJson[T](Json.parse(response.body)).asOpt

  private def givenThereIs(c: Case): Unit = storeCases(c)

  private def givenThereIs(e: Event): Unit = storeEvents(e)

}
