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

import java.time._
import java.time.temporal.ChronoUnit
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.bindingtariffclassification.component.utils.AppConfigWithAFixedDate
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus._
import uk.gov.hmrc.bindingtariffclassification.model.{Case, CaseStatus, Event}
import uk.gov.hmrc.bindingtariffclassification.scheduler.ReferredDaysElapsedJob
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import uk.gov.hmrc.http.HeaderCarrier
import util.CaseData._
import util.{EventData, TestMetrics}

import scala.concurrent.Await.result

class ReferredDaysElapsedSpec extends BaseFeatureSpec with MockitoSugar {

  protected val serviceUrl = s"http://localhost:$port"

  override lazy val app: Application = new GuiceApplicationBuilder()
    .bindings(bind[AppConfig].to[AppConfigWithAFixedDate])
    .configure("metrics.enabled" -> false)
    .configure("mongodb.uri" -> "mongodb://localhost:27017/test-ClassificationMongoRepositoryTest")
    .overrides(bind[Metrics].toInstance(new TestMetrics))
    .build()

  private val job: ReferredDaysElapsedJob = app.injector.instanceOf[ReferredDaysElapsedJob]

  // Get the current year dynamically from the test clock
  private val currentDate  = LocalDate.now(appConfig.clock)
  private val currentYear  = currentDate.getYear
  private val previousYear = currentYear - 1

  private val testDates = Map(
    "case1"     -> s"$previousYear-12-20",
    "case2"     -> s"$previousYear-12-30",
    "case3"     -> s"$currentYear-01-10",
    "case4"     -> s"$currentYear-02-03",
    "case5"     -> s"$currentYear-02-01",
    "completed" -> s"$currentYear-02-01"
  )

  private def calculateExpectedWorkingDays(startDateStr: String, endDate: LocalDate): Long = {
    val startDate = LocalDate.parse(startDateStr)

    implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
    val bankHolidaysConnector =
      app.injector.instanceOf[uk.gov.hmrc.bindingtariffclassification.connector.BankHolidaysConnector]
    val allBankHolidays = result(bankHolidaysConnector.get(), timeout)

    val relevantBankHolidays =
      allBankHolidays.filter(holiday => !holiday.isBefore(startDate) && holiday.isBefore(endDate))

    val totalDays = ChronoUnit.DAYS.between(startDate, endDate)
    val workingDays = (0L until totalDays)
      .map(startDate.plusDays)
      .filterNot(relevantBankHolidays.contains)
      .filterNot(date => date.getDayOfWeek == DayOfWeek.SATURDAY || date.getDayOfWeek == DayOfWeek.SUNDAY)
      .length

    workingDays.toLong
  }

  Feature("Referred Days Elapsed Job") {
    Scenario("Calculates elapsed days for REFERRED cases with dynamic dates") {
      Given("There are cases with mixed statuses in the database using dynamic dates")

      // Create cases with dynamic dates
      givenThereIs(aCaseWith(reference = "ref-case1", status = REFERRED, createdDate = testDates("case1")))
      givenThereIs(aStatusChangeWith("ref-case1", CaseStatus.REFERRED, testDates("case1")))

      givenThereIs(aCaseWith(reference = "ref-case2", status = REFERRED, createdDate = testDates("case2")))
      givenThereIs(aStatusChangeWith("ref-case2", CaseStatus.REFERRED, testDates("case2")))

      givenThereIs(aCaseWith(reference = "ref-case3", status = REFERRED, createdDate = testDates("case3")))
      givenThereIs(aStatusChangeWith("ref-case3", CaseStatus.REFERRED, testDates("case3")))

      givenThereIs(aCaseWith(reference = "ref-case4", status = REFERRED, createdDate = testDates("case4")))
      givenThereIs(aStatusChangeWith("ref-case4", CaseStatus.REFERRED, testDates("case4")))

      givenThereIs(aCaseWith(reference = "ref-case5", status = REFERRED, createdDate = testDates("case5")))
      givenThereIs(aStatusChangeWith("ref-case5", CaseStatus.REFERRED, testDates("case5")))

      givenThereIs(aCaseWith(reference = "completed", status = COMPLETED, createdDate = testDates("completed")))
      givenThereIs(aStatusChangeWith("completed", CaseStatus.REFERRED, testDates("completed")))

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Referred Days Elapsed should be correct based on dynamic calculation")

      // Calculate expected values dynamically
      val expectedCase1 = calculateExpectedWorkingDays(testDates("case1"), currentDate)
      val expectedCase2 = calculateExpectedWorkingDays(testDates("case2"), currentDate)
      val expectedCase3 = calculateExpectedWorkingDays(testDates("case3"), currentDate)
      val expectedCase4 = calculateExpectedWorkingDays(testDates("case4"), currentDate)
      val expectedCase5 = calculateExpectedWorkingDays(testDates("case5"), currentDate)

      // Assert with dynamically calculated expected values
      referredDaysElapsedForCase("ref-case1") shouldBe expectedCase1
      referredDaysElapsedForCase("ref-case2") shouldBe expectedCase2
      referredDaysElapsedForCase("ref-case3") shouldBe expectedCase3
      referredDaysElapsedForCase("ref-case4") shouldBe expectedCase4
      referredDaysElapsedForCase("ref-case5") shouldBe expectedCase5
      referredDaysElapsedForCase("completed") shouldBe -1 // Should not be updated
    }

    Scenario("Calculates elapsed days for SUSPENDED cases with dynamic dates") {
      Given("There are cases with mixed statuses in the database using dynamic dates")

      givenThereIs(aCaseWith(reference = "s-ref-case1", status = SUSPENDED, createdDate = testDates("case1")))
      givenThereIs(aStatusChangeWith("s-ref-case1", CaseStatus.SUSPENDED, testDates("case1")))

      givenThereIs(aCaseWith(reference = "s-ref-case2", status = SUSPENDED, createdDate = testDates("case2")))
      givenThereIs(aStatusChangeWith("s-ref-case2", CaseStatus.SUSPENDED, testDates("case2")))

      givenThereIs(aCaseWith(reference = "s-ref-case3", status = SUSPENDED, createdDate = testDates("case3")))
      givenThereIs(aStatusChangeWith("s-ref-case3", CaseStatus.SUSPENDED, testDates("case3")))

      givenThereIs(aCaseWith(reference = "s-ref-case4", status = SUSPENDED, createdDate = testDates("case4")))
      givenThereIs(aStatusChangeWith("s-ref-case4", CaseStatus.SUSPENDED, testDates("case4")))

      givenThereIs(aCaseWith(reference = "s-ref-case5", status = SUSPENDED, createdDate = testDates("case5")))
      givenThereIs(aStatusChangeWith("s-ref-case5", CaseStatus.SUSPENDED, testDates("case5")))

      givenThereIs(aCaseWith(reference = "s-completed", status = COMPLETED, createdDate = testDates("completed")))
      givenThereIs(aStatusChangeWith("s-completed", CaseStatus.SUSPENDED, testDates("completed")))

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Referred Days Elapsed should be correct based on dynamic calculation")

      // Use the same dynamic calculations
      val expectedCase1 = calculateExpectedWorkingDays(testDates("case1"), currentDate)
      val expectedCase2 = calculateExpectedWorkingDays(testDates("case2"), currentDate)
      val expectedCase3 = calculateExpectedWorkingDays(testDates("case3"), currentDate)
      val expectedCase4 = calculateExpectedWorkingDays(testDates("case4"), currentDate)
      val expectedCase5 = calculateExpectedWorkingDays(testDates("case5"), currentDate)

      referredDaysElapsedForCase("s-ref-case1") shouldBe expectedCase1
      referredDaysElapsedForCase("s-ref-case2") shouldBe expectedCase2
      referredDaysElapsedForCase("s-ref-case3") shouldBe expectedCase3
      referredDaysElapsedForCase("s-ref-case4") shouldBe expectedCase4
      referredDaysElapsedForCase("s-ref-case5") shouldBe expectedCase5
      referredDaysElapsedForCase("s-completed") shouldBe -1
    }
  }

  private def toInstant(date: String) =
    LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC)

  private def aCaseWith(reference: String, createdDate: String, status: CaseStatus): Case =
    createCase(app = createBasicBTIApplication).copy(
      reference = reference,
      createdDate = LocalDate.parse(createdDate).atStartOfDay().toInstant(ZoneOffset.UTC),
      status = status,
      referredDaysElapsed = -1
    )

  private def aStatusChangeWith(caseReference: String, status: CaseStatus, date: String): Event =
    EventData
      .createCaseStatusChangeEvent(caseReference, from = OPEN, to = status)
      .copy(timestamp = toInstant(date))

  private def givenThereIs(c: Case)  = storeCases(c)
  private def givenThereIs(c: Event) = storeEvents(c)

  private def referredDaysElapsedForCase: String => Long = { reference =>
    getCase(reference).map(_.referredDaysElapsed).getOrElse(0)
  }
}
