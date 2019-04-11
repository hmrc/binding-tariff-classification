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
import play.api.http.Status.OK
import play.api.libs.json.{Json, Reads}
import scalaj.http.{Http, HttpResponse}
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.reporting.ReportResult
import util.Cases._

class ReportSpec extends BaseFeatureSpec {

  override lazy val port = 14682
  protected val serviceUrl = s"http://localhost:$port"

  feature("Report") {

    scenario("Generate a Report on Days Elapsed Grouping by Queue") {
      Given("There are some documents in the collection")
      givenThereIs(aCase(withQueue("queue-1"), withDaysElapsed(1)))
      givenThereIs(aCase(withQueue("queue-1"), withDaysElapsed(2)))

      When("I request the report")
      val result = whenIGET("report?report_field=days-elapsed&report_group=queue-id")

      Then("The response code should be 204")
      result.code shouldBe OK

      And("The response body is empty")
      thenTheJsonBodyOf[Seq[ReportResult]](result) shouldBe Some(Seq(ReportResult("queue-1", Seq(1, 2))))
    }

  }

  private def whenIGET(path: String): HttpResponse[String] = Http(s"$serviceUrl/$path")
    .header(apiTokenKey, appConfig.authorization)
    .method(HttpVerbs.GET)
    .asString

  private def thenTheJsonBodyOf[T](response: HttpResponse[String])(implicit rds: Reads[T]): Option[T] = Json.fromJson[T](Json.parse(response.body)).asOpt

  private def givenThereIs(c: Case): Unit = storeCases(c)

}
