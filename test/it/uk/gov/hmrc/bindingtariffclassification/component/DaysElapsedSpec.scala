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

import java.time.{Instant, LocalDate, ZoneOffset}

import play.api.http.HttpVerbs
import play.api.http.Status._
import scalaj.http.Http
import uk.gov.hmrc.bindingtariffclassification.model.{Case, CaseStatus}
import uk.gov.hmrc.bindingtariffclassification.scheduler.DaysElapsedJob
import util.CaseData._

import scala.concurrent.Await.result

class DaysElapsedSpec extends BaseFeatureSpec {

  override lazy val port = 14683
  protected val serviceUrl = s"http://localhost:$port"

  private val c: Case = createCase(app = createBasicBTIApplication)

  private val job: DaysElapsedJob = app.injector.instanceOf[DaysElapsedJob]

  feature("Days Elapsed Endpoint") {

    scenario("Updates Cases with status NEW and OPEN") {

      Given("There are cases with mixed statuses in the database")
      storeFewCases()

      val locks = schedulerLockStoreSize

      When("I hit the days-elapsed endpoint")
      val result = Http(s"$serviceUrl/scheduler/days-elapsed")
        .method(HttpVerbs.PUT)
        .asString

      Then("The response code should be 204")
      result.code shouldEqual NO_CONTENT

      // Then
      assertDaysElapsed()

      Then("A new scheduler lock has not been created in mongo")
      assertLocksDidNotIncrement(locks)
    }

  }

  feature("Days Elapsed Job") {

    scenario("Updates Cases with status NEW and OPEN") {

      Given("There are cases with mixed statuses in the database")
      storeFewCases()

      val locks = schedulerLockStoreSize

      When("The job runs")
      result(job.execute(), timeout)

      Then("The days elapsed field is incremented appropriately")
      assertDaysElapsed()

      Then("A new scheduler lock has not been created in mongo")
      assertLocksDidNotIncrement(locks)
    }

  }

  private def storeFewCases(): Unit = {
    val newCase = c.copy(reference = "new", status = CaseStatus.NEW, daysElapsed = 0)
    val openCase = c.copy(reference = "open", status = CaseStatus.OPEN, daysElapsed = 0)
    val otherCase = c.copy(reference = "other", status = CaseStatus.SUSPENDED, daysElapsed = 0)
    storeCases(newCase, openCase, otherCase)
  }

  private def assertDaysElapsed(): Unit = {
    val currentDate = Instant.now().atOffset(ZoneOffset.UTC).toLocalDate

    if (isNonWorkingDay(currentDate)) assertWorkInProgressCases(0)
    else assertWorkInProgressCases(1)

    getCase("other").map(_.daysElapsed) shouldBe Some(0)
  }

  private def assertLocksDidNotIncrement(initialNumberOfLocks: Int): Unit = {
    schedulerLockStoreSize shouldBe initialNumberOfLocks
  }

  private def isNonWorkingDay(date: LocalDate): Boolean = {
    job.isWeekend(date) || result[Boolean](job.isBankHoliday(date), timeout)
  }

  private def assertWorkInProgressCases(expectedDaysElapsed: Int) = {
    getCase("new").map(_.daysElapsed) shouldBe Some(expectedDaysElapsed)
    getCase("open").map(_.daysElapsed) shouldBe Some(expectedDaysElapsed)
  }

}
