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
import uk.gov.hmrc.bindingtariffclassification.model.{Case, Event}
import uk.gov.hmrc.bindingtariffclassification.scheduler.ActiveDaysElapsedJob
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import uk.gov.hmrc.http.HeaderCarrier
import util.CaseData._
import util.{EventData, TestMetrics}

import scala.concurrent.Await.result

class ActiveDaysElapsedSpec extends BaseFeatureSpec with MockitoSugar {

  protected val serviceUrl = s"http://localhost:$port"

  override lazy val app: Application = new GuiceApplicationBuilder()
    .bindings(bind[AppConfig].to[AppConfigWithAFixedDate])
    .configure("metrics.enabled" -> false)
    .configure("mongodb.uri" -> "mongodb://localhost:27017/test-ClassificationMongoRepositoryTest")
    .overrides(bind[Metrics].toInstance(new TestMetrics))
    .build()

  private val job: ActiveDaysElapsedJob = app.injector.instanceOf[ActiveDaysElapsedJob]

  // Get the current year dynamically from the test clock
  private val currentDate  = LocalDate.now(appConfig.clock)
  private val currentYear  = currentDate.getYear
  private val previousYear = currentYear - 1

  private val testDates = Map(
    "case1"      -> s"$previousYear-12-20",
    "case2"      -> s"$previousYear-12-30",
    "case3"      -> s"$currentYear-01-10",
    "case4"      -> s"$currentYear-02-03",
    "case5"      -> s"$currentYear-02-01",
    "liability1" -> s"$previousYear-12-20",
    "liability2" -> s"$previousYear-12-30",
    "liability3" -> s"$previousYear-12-10",
    "liability4" -> s"$previousYear-12-20",
    "migrated1"  -> s"$previousYear-12-20",
    "migrated2"  -> s"$previousYear-12-30",
    "migrated3"  -> s"$previousYear-12-31",
    "migrated4"  -> s"$currentYear-01-10",
    "migrated5"  -> s"$currentYear-01-15",
    "migrated6"  -> s"$currentYear-01-20"
  )

  private def calculateExpectedWorkingDays(
    startDateStr: String,
    endDate: LocalDate,
    excludeDates: Set[LocalDate] = Set.empty
  ): Long = {
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
      .filterNot(excludeDates.contains)
      .filterNot(date => date.getDayOfWeek == DayOfWeek.SATURDAY || date.getDayOfWeek == DayOfWeek.SUNDAY)
      .length

    workingDays.toLong
  }

  Feature("Days Elapsed Job") {
    Scenario("Calculates elapsed days for OPEN & NEW cases with dynamic dates") {
      Given("There are cases with mixed statuses in the database using dynamic dates")

      givenThereIs(aCaseWith(reference = "ref-case1", status = OPEN, createdDate = testDates("case1")))
      givenThereIs(aCaseWith(reference = "ref-case2", status = NEW, createdDate = testDates("case2")))
      givenThereIs(aCaseWith(reference = "ref-case3", status = OPEN, createdDate = testDates("case3")))
      givenThereIs(aCaseWith(reference = "ref-case4", status = NEW, createdDate = testDates("case4")))
      givenThereIs(aCaseWith(reference = "ref-case5", status = NEW, createdDate = testDates("case5")))
      givenThereIs(aCaseWith(reference = "completed", status = COMPLETED, createdDate = testDates("case5")))

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct based on dynamic calculation")

      val expectedCase1 = calculateExpectedWorkingDays(testDates("case1"), currentDate)
      val expectedCase2 = calculateExpectedWorkingDays(testDates("case2"), currentDate)
      val expectedCase3 = calculateExpectedWorkingDays(testDates("case3"), currentDate)
      val expectedCase4 = calculateExpectedWorkingDays(testDates("case4"), currentDate)
      val expectedCase5 = calculateExpectedWorkingDays(testDates("case5"), currentDate)

      daysElapsedForCase("ref-case1") shouldBe expectedCase1
      daysElapsedForCase("ref-case2") shouldBe expectedCase2
      daysElapsedForCase("ref-case3") shouldBe expectedCase3
      daysElapsedForCase("ref-case4") shouldBe expectedCase4
      daysElapsedForCase("ref-case5") shouldBe expectedCase5
      daysElapsedForCase("completed") shouldBe -1 // Unchanged
    }

    Scenario("Calculates elapsed days for OPEN & NEW Liability cases with dynamic dates") {
      Given("There are liability cases with mixed statuses in the database using dynamic dates")

      // OPEN without date of receipt will take created date
      givenThereIs(
        aLiabilityCaseWith(
          reference = "ref-1",
          status = OPEN,
          createdDate = testDates("liability1"),
          dateOfReceipt = None
        )
      )
      givenThereIs(
        aLiabilityCaseWith(
          reference = "ref-2",
          status = OPEN,
          createdDate = testDates("liability2"),
          dateOfReceipt = Some(testDates("liability1"))
        )
      )

      givenThereIs(
        aLiabilityCaseWith(
          reference = "ref-3",
          status = NEW,
          createdDate = testDates("liability3"),
          dateOfReceipt = None
        )
      )
      givenThereIs(
        aLiabilityCaseWith(
          reference = "ref-4",
          status = NEW,
          createdDate = testDates("liability4"),
          dateOfReceipt = Some(testDates("liability3"))
        )
      )

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct based on dynamic calculation")

      val expectedRef1 = calculateExpectedWorkingDays(testDates("liability1"), currentDate)
      val expectedRef2 = calculateExpectedWorkingDays(testDates("liability1"), currentDate)
      val expectedRef3 = calculateExpectedWorkingDays(testDates("liability3"), currentDate)
      val expectedRef4 = calculateExpectedWorkingDays(testDates("liability3"), currentDate)

      daysElapsedForCase("ref-1") shouldBe expectedRef1
      daysElapsedForCase("ref-2") shouldBe expectedRef2
      daysElapsedForCase("ref-3") shouldBe expectedRef3
      daysElapsedForCase("ref-4") shouldBe expectedRef4
    }

    Scenario("Calculates elapsed days for a referred case with dynamic dates") {
      Given("A Case which was REFERRED in the past using dynamic dates")

      val referredDate = s"$currentYear-01-15"

      givenThereIs(aCaseWith(reference = "valid-ref", status = OPEN, createdDate = testDates("case3")))
      givenThereIs(aStatusChangeWith(caseReference = "valid-ref", status = REFERRED, date = referredDate))

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct based on dynamic calculation")

      val referralStart = LocalDate.parse(referredDate)
      val daysToExclude = (0L until ChronoUnit.DAYS.between(referralStart, currentDate))
        .map(referralStart.plusDays)
        .toSet

      val expectedDays = calculateExpectedWorkingDays(testDates("case3"), currentDate, daysToExclude)

      daysElapsedForCase("valid-ref") shouldBe expectedDays
    }

    Scenario("Calculates elapsed days for a case created & referred on the same day with dynamic dates") {
      Given("There is case which was REFERRED the day it was created using dynamic dates")

      givenThereIs(aCaseWith(reference = "valid-ref", status = OPEN, createdDate = testDates("case5")))
      givenThereIs(aStatusChangeWith(caseReference = "valid-ref", status = REFERRED, date = testDates("case5")))

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct")

      val expectedDays = calculateExpectedWorkingDays(testDates("case5"), currentDate)
      daysElapsedForCase("valid-ref") shouldBe expectedDays
    }

    Scenario("Calculates elapsed days for a case referred today with dynamic dates") {
      Given("There is case with a referred case using dynamic dates")

      givenThereIs(aCaseWith(reference = "valid-ref", status = OPEN, createdDate = testDates("case4")))
      givenThereIs(aStatusChangeWith(caseReference = "valid-ref", status = REFERRED, date = testDates("case4")))

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct")
      daysElapsedForCase("valid-ref") shouldBe 0
    }

    Scenario("Calculates elapsed days for a suspended case with dynamic dates") {
      Given("A Case which was SUSPENDED in the past using dynamic dates")

      val suspendedDate = s"$currentYear-01-15"

      givenThereIs(aCaseWith(reference = "valid-ref", status = OPEN, createdDate = testDates("case3")))
      givenThereIs(aStatusChangeWith(caseReference = "valid-ref", status = SUSPENDED, date = suspendedDate))

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct based on dynamic calculation")

      val suspensionStart = LocalDate.parse(suspendedDate)
      val daysToExclude = (0L until ChronoUnit.DAYS.between(suspensionStart, currentDate))
        .map(suspensionStart.plusDays)
        .toSet

      val expectedDays = calculateExpectedWorkingDays(testDates("case3"), currentDate, daysToExclude)

      daysElapsedForCase("valid-ref") shouldBe expectedDays
    }

    Scenario("Calculates elapsed days for a case created & suspended on the same day with dynamic dates") {
      Given("There is case which was SUSPENDED the day it was created using dynamic dates")

      givenThereIs(aCaseWith(reference = "valid-ref", status = OPEN, createdDate = testDates("case5")))
      givenThereIs(aStatusChangeWith(caseReference = "valid-ref", status = SUSPENDED, date = testDates("case5")))

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct")

      val expectedDays = calculateExpectedWorkingDays(testDates("case5"), currentDate)
      daysElapsedForCase("valid-ref") shouldBe expectedDays
    }

    Scenario("Calculates elapsed days for a case suspended today with dynamic dates") {
      Given("There is case with a suspended case using dynamic dates")

      givenThereIs(aCaseWith(reference = "valid-ref", status = OPEN, createdDate = testDates("case4")))
      givenThereIs(aStatusChangeWith(caseReference = "valid-ref", status = SUSPENDED, date = testDates("case4")))

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct")
      daysElapsedForCase("valid-ref") shouldBe 0
    }

    Scenario("Calculates elapsed days for migrated cases with dynamic dates") {
      Given("There are migrated cases with mixed statuses in the database using dynamic dates")

      val extractDate1 = s"$currentYear-01-21"
      val extractDate2 = s"$currentYear-01-30"
      val extractDate3 = testDates("case4") // Current date

      givenThereIs(
        aMigratedCaseWith(
          reference = "mref-case1",
          status = OPEN,
          createdDate = testDates("migrated1"),
          dateOfExtract = extractDate1,
          migratedDaysElapsed = 19L
        )
      )
      givenThereIs(
        aMigratedCaseWith(
          reference = "mref-case2",
          status = NEW,
          createdDate = testDates("migrated2"),
          dateOfExtract = extractDate1,
          migratedDaysElapsed = 10L
        )
      )
      givenThereIs(
        aMigratedCaseWith(
          reference = "mref-case3",
          status = OPEN,
          createdDate = testDates("migrated3"),
          dateOfExtract = extractDate2,
          migratedDaysElapsed = 8L
        )
      )
      givenThereIs(
        aMigratedCaseWith(
          reference = "mref-case4",
          status = NEW,
          createdDate = testDates("migrated4"),
          dateOfExtract = extractDate2,
          migratedDaysElapsed = 5L
        )
      )
      givenThereIs(
        aMigratedCaseWith(
          reference = "mref-case5",
          status = NEW,
          createdDate = testDates("migrated5"),
          dateOfExtract = extractDate3,
          migratedDaysElapsed = 1L
        )
      )
      givenThereIs(
        aMigratedCaseWith(
          reference = "mcompleted",
          status = COMPLETED,
          createdDate = testDates("migrated6"),
          dateOfExtract = extractDate2,
          migratedDaysElapsed = 1L
        )
      )

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct based on dynamic calculation")

      val trackingDays1 = calculateExpectedWorkingDays(extractDate1, currentDate)
      val trackingDays2 = calculateExpectedWorkingDays(extractDate2, currentDate)
      val trackingDays3 = calculateExpectedWorkingDays(extractDate3, currentDate)

      daysElapsedForCase("mref-case1") shouldBe (19L + trackingDays1)
      daysElapsedForCase("mref-case2") shouldBe (10L + trackingDays1)
      daysElapsedForCase("mref-case3") shouldBe (8L + trackingDays2)
      daysElapsedForCase("mref-case4") shouldBe (5L + trackingDays2)
      daysElapsedForCase("mref-case5") shouldBe (1L + trackingDays3)
      daysElapsedForCase("mcompleted") shouldBe -1 // Unchanged
    }
  }

  private def toInstant(date: String) =
    LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC)

  private def aCaseWith(reference: String, createdDate: String, status: CaseStatus): Case =
    createCase(app = createBasicBTIApplication).copy(
      reference = reference,
      createdDate = LocalDate.parse(createdDate).atStartOfDay().toInstant(ZoneOffset.UTC),
      status = status,
      daysElapsed = -1
    )

  private def aMigratedCaseWith(
    reference: String,
    createdDate: String,
    status: CaseStatus,
    dateOfExtract: String,
    migratedDaysElapsed: Long
  ): Case =
    aCaseWith(reference, createdDate, status).copy(
      dateOfExtract = Some(LocalDate.parse(dateOfExtract).atStartOfDay().toInstant(ZoneOffset.UTC)),
      migratedDaysElapsed = Some(migratedDaysElapsed)
    )

  private def aLiabilityCaseWith(
    reference: String,
    createdDate: String,
    status: CaseStatus,
    dateOfReceipt: Option[String]
  ): Case = {
    val liability = createLiabilityOrder
    createCase(app = liability).copy(
      reference = reference,
      createdDate = LocalDate.parse(createdDate).atStartOfDay().toInstant(ZoneOffset.UTC),
      status = status,
      daysElapsed = -1,
      application = liability.copy(
        dateOfReceipt = dateOfReceipt.map(LocalDate.parse(_).atStartOfDay().toInstant(ZoneOffset.UTC))
      )
    )
  }

  private def aStatusChangeWith(caseReference: String, status: CaseStatus, date: String): Event =
    EventData
      .createCaseStatusChangeEvent(caseReference, from = OPEN, to = status)
      .copy(timestamp = toInstant(date))

  private def givenThereIs(c: Case)  = storeCases(c)
  private def givenThereIs(c: Event) = storeEvents(c)

  private def daysElapsedForCase: String => Long = { reference => getCase(reference).map(_.daysElapsed).getOrElse(0) }

}
