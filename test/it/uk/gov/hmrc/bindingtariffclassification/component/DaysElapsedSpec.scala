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

import java.time._

import javax.inject.Inject
import org.scalatest.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceInjectorBuilder}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.bindingtariffclassification.component.utils.MockAppConfig
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.{Case, CaseStatus}
import uk.gov.hmrc.bindingtariffclassification.scheduler.DaysElapsedJob
import util.CaseData._

import scala.concurrent.Await.result

class DaysElapsedSpec extends BaseFeatureSpec with MockitoSugar {

  override lazy val port = 14683
  protected val serviceUrl = s"http://localhost:$port"

  private val c: Case = createCase(app = createBasicBTIApplication)


//  val application = new GuiceApplicationBuilder()
//    .bindings(bind[AppConfig].to[MockAppConfig])
//    //.overrides(bind[AppConfig].to[MockAppConfig])
//    .build()
//
  val injector = new GuiceApplicationBuilder()
    .bindings(bind[AppConfig].to[MockAppConfig])
  .disable[com.kenshoo.play.metrics.PlayModule]
  .configure("metrics.enabled" -> false)
 //   .overrides(bind[AppConfig].to[MockAppConfig])
    .injector()

//  val injector = new GuiceInjectorBuilder()
//    .overrides(bind[AppConfig].to[MockAppConfig])
//    .injector()

  private val job: DaysElapsedJob = injector.instanceOf[DaysElapsedJob]


//  feature("Days Elapsed Endpoint") {
//
//    scenario("Updates Cases with status NEW and OPEN") {
//
//      Given("There are cases with mixed statuses in the database")
//      storeFewCases()
//
//      val locks = schedulerLockStoreSize
//
//      When("I hit the days-elapsed endpoint")
//      val result = Http(s"$serviceUrl/scheduler/days-elapsed")
//        .header(apiTokenKey, appConfig.authorization)
//        .method(HttpVerbs.PUT)
//        .asString
//
//      Then("The response code should be 204")
//      result.code shouldEqual NO_CONTENT
//
//      // Then
//      assertDaysElapsed()
//
//      Then("A new scheduler lock has not been created in mongo")
//      assertLocksDidNotIncrement(locks)
//    }
//
//  }

  feature("Days Elapsed Job") {

    scenario("Updates Cases with status NEW and OPEN") {

      Given("There are cases with mixed statuses in the database")
      storeFewCases()

      When("The job runs")
      result(job.execute(), timeout)

      Then("The days elapsed field is incremented appropriately")
      assertDaysElapsed()
    }

  }

  private def storeFewCases(): Unit = {
    val newCase = c.copy(reference = "from20181220", status = CaseStatus.NEW, createdDate = dateFrom("2018-12-20"))
    val openCase = c.copy(reference = "from20181230", status = CaseStatus.OPEN,  createdDate = dateFrom("2018-12-30"))
    val otherCase = c.copy(reference = "from20190110", status = CaseStatus.NEW,  createdDate = dateFrom("2019-01-10"))
    storeCases(newCase, openCase, otherCase)
  }

  private def dateFrom(from : String ) : Instant = {
    LocalDate.parse(from).atStartOfDay().toInstant(ZoneOffset.UTC)
  }

  private def assertDaysElapsed(): Unit = {
    daysElapsedFrom("from20181220") shouldBe 6 //
    daysElapsedFrom("from20181230") shouldBe 1 //???
    daysElapsedFrom("from20190110") shouldBe 16 // OK!
  }

  private def daysElapsedFrom(caseReference : String) = {
    getCase(caseReference).map(_.daysElapsed).getOrElse(0)
  }

}
