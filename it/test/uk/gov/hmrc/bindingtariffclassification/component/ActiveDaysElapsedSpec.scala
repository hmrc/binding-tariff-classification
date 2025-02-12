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

  Feature("Days Elapsed Job") {
    Scenario("Calculates elapsed days for OPEN & NEW cases") {
      Given("There are cases with mixed statuses in the database")

      givenThereIs(aCaseWith(reference = "ref-20181220", status = OPEN, createdDate = "2018-12-20"))
      givenThereIs(aCaseWith(reference = "ref-20181230", status = NEW, createdDate = "2018-12-30"))
      givenThereIs(aCaseWith(reference = "ref-20190110", status = OPEN, createdDate = "2019-01-10"))
      givenThereIs(aCaseWith(reference = "ref-20190203", status = NEW, createdDate = "2019-02-03"))
      givenThereIs(aCaseWith(reference = "ref-20190201", status = NEW, createdDate = "2019-02-01"))
      givenThereIs(aCaseWith(reference = "completed", status = COMPLETED, createdDate = "2019-02-01"))

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct")
      daysElapsedForCase("ref-20181220") shouldBe 31
      daysElapsedForCase("ref-20181230") shouldBe 24
      daysElapsedForCase("ref-20190110") shouldBe 17
      daysElapsedForCase("ref-20190203") shouldBe 0
      daysElapsedForCase("ref-20190201") shouldBe 1
      daysElapsedForCase("completed")    shouldBe -1 // Unchanged
    }

    Scenario("Calculates elapsed days for OPEN & NEW Liability cases") {
      Given("There are cases with mixed statuses in the database")

      //OPEN
      //without date of receipt will take created date
      givenThereIs(
        aLiabilityCaseWith(reference = "ref-1", status = OPEN, createdDate = "2018-12-20", dateOfReceipt = None)
      )
      //with date of receipt will take date of receipt
      givenThereIs(
        aLiabilityCaseWith(
          reference = "ref-2",
          status = OPEN,
          createdDate = "2018-12-30",
          dateOfReceipt = Some("2018-12-20")
        )
      )

      //NEW
      //without date of receipt will take created date
      givenThereIs(
        aLiabilityCaseWith(reference = "ref-3", status = NEW, createdDate = "2018-12-10", dateOfReceipt = None)
      )
      //with date of receipt will take date of receipt
      givenThereIs(
        aLiabilityCaseWith(
          reference = "ref-4",
          status = NEW,
          createdDate = "2018-12-20",
          dateOfReceipt = Some("2018-12-10")
        )
      )

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct")
      daysElapsedForCase("ref-1") shouldBe 31
      daysElapsedForCase("ref-2") shouldBe 31

      daysElapsedForCase("ref-3") shouldBe 39
      daysElapsedForCase("ref-4") shouldBe 39
    }

    Scenario("Calculates elapsed days for a referred case") {
      Given("A Case which was REFERRED in the past")
      givenThereIs(aCaseWith(reference = "valid-ref", status = OPEN, createdDate = "2019-01-10"))
      givenThereIs(aStatusChangeWith(caseReference = "valid-ref", status = REFERRED, date = "2019-01-15"))

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct")
      daysElapsedForCase("valid-ref") shouldBe 3
    }

    Scenario("Calculates elapsed days for a case created & referred on the same day") {
      Given("There is case which was REFERRED the day it was created")
      givenThereIs(aCaseWith(reference = "valid-ref", status = OPEN, createdDate = "2019-02-01"))
      givenThereIs(aStatusChangeWith(caseReference = "valid-ref", status = REFERRED, date = "2019-02-01"))

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct")
      daysElapsedForCase("valid-ref") shouldBe 0
    }

    Scenario("Calculates elapsed days for a case referred today") {
      Given("There is case with a referred case")
      givenThereIs(aCaseWith(reference = "valid-ref", status = OPEN, createdDate = "2019-02-03"))
      givenThereIs(aStatusChangeWith(caseReference = "valid-ref", status = REFERRED, date = "2019-02-03"))

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct")
      daysElapsedForCase("valid-ref") shouldBe 0
    }

    Scenario("Calculates elapsed days for a suspended case") {
      Given("A Case which was SUSPENDED in the past")
      givenThereIs(aCaseWith(reference = "valid-ref", status = OPEN, createdDate = "2019-01-10"))
      givenThereIs(aStatusChangeWith(caseReference = "valid-ref", status = SUSPENDED, date = "2019-01-15"))

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct")
      daysElapsedForCase("valid-ref") shouldBe 3
    }

    Scenario("Calculates elapsed days for a case created & suspended on the same day") {
      Given("There is case which was REFERRED the day it was created")
      givenThereIs(aCaseWith(reference = "valid-ref", status = OPEN, createdDate = "2019-02-01"))
      givenThereIs(aStatusChangeWith(caseReference = "valid-ref", status = SUSPENDED, date = "2019-02-01"))

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct")
      daysElapsedForCase("valid-ref") shouldBe 0
    }

    Scenario("Calculates elapsed days for a case suspended today") {
      Given("There is case with a referred case")
      givenThereIs(aCaseWith(reference = "valid-ref", status = OPEN, createdDate = "2019-02-03"))
      givenThereIs(aStatusChangeWith(caseReference = "valid-ref", status = SUSPENDED, date = "2019-02-03"))

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct")
      daysElapsedForCase("valid-ref") shouldBe 0
    }

    Scenario("Calculates elapsed days for migrated cases") {
      Given("There are migrated cases with mixed statuses in the database")

      givenThereIs(
        aMigratedCaseWith(
          reference = "mref-20181220",
          status = OPEN,
          createdDate = "2018-12-20",
          dateOfExtract = "2019-01-21",
          migratedDaysElapsed = 19L
        )
      )
      givenThereIs(
        aMigratedCaseWith(
          reference = "mref-20181230",
          status = NEW,
          createdDate = "2018-12-30",
          dateOfExtract = "2019-01-21",
          migratedDaysElapsed = 10L
        )
      )
      givenThereIs(
        aMigratedCaseWith(
          reference = "mref-20181231",
          status = OPEN,
          createdDate = "2018-12-31",
          dateOfExtract = "2019-01-30",
          migratedDaysElapsed = 8L
        )
      )
      givenThereIs(
        aMigratedCaseWith(
          reference = "mref-20190110",
          status = NEW,
          createdDate = "2019-01-10",
          dateOfExtract = "2019-01-30",
          migratedDaysElapsed = 5L
        )
      )
      givenThereIs(
        aMigratedCaseWith(
          reference = "mref-20190115",
          status = NEW,
          createdDate = "2019-01-15",
          dateOfExtract = "2019-02-03",
          migratedDaysElapsed = 1L
        )
      )
      givenThereIs(
        aMigratedCaseWith(
          reference = "mcompleted",
          status = COMPLETED,
          createdDate = "2019-01-20",
          dateOfExtract = "2019-01-30",
          migratedDaysElapsed = 1L
        )
      )

      When("The job runs")
      result(job.execute(), timeout)

      Then("The Days Elapsed should be correct")
      daysElapsedForCase("mref-20181220") shouldBe 29
      daysElapsedForCase("mref-20181230") shouldBe 20
      daysElapsedForCase("mref-20181231") shouldBe 11
      daysElapsedForCase("mref-20190110") shouldBe 8
      daysElapsedForCase("mref-20190115") shouldBe 1
      daysElapsedForCase("mcompleted")    shouldBe -1 // Unchanged
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
