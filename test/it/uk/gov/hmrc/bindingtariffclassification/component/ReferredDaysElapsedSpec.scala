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

import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.bindingtariffclassification.component.utils.AppConfigWithAFixedDate
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus._
import uk.gov.hmrc.bindingtariffclassification.model.{Case, CaseStatus, Event}
import uk.gov.hmrc.bindingtariffclassification.scheduler.ReferredDaysElapsedJob
import util.CaseData._
import util.EventData

import scala.concurrent.Await.result

class ReferredDaysElapsedSpec extends BaseFeatureSpec with MockitoSugar {

  override lazy val port = 14683
  protected val serviceUrl = s"http://localhost:$port"

  private val injector = new GuiceApplicationBuilder()
    .bindings(bind[AppConfig].to[AppConfigWithAFixedDate])
    .disable[com.kenshoo.play.metrics.PlayModule]
    .configure("metrics.enabled" -> false)
    .configure("mongodb.uri" -> "mongodb://localhost:27017/test-ClassificationMongoRepositoryTest")
    .injector()

  private val job: ReferredDaysElapsedJob = injector.instanceOf[ReferredDaysElapsedJob]

  feature("Referred Days Elapsed Job") {
    scenario("Calculates elapsed days for REFERRED cases") {
      Given("There are cases with mixed statuses in the database")

      givenThereIs(aCaseWith(reference = "ref-20181220", status = REFERRED, createdDate = "2018-12-20"))
      givenThereIs(aStatusChangeWith("ref-20181220", CaseStatus.REFERRED, "2018-12-20"))

      givenThereIs(aCaseWith(reference = "ref-20181230", status = REFERRED, createdDate = "2018-12-30"))
      givenThereIs(aStatusChangeWith("ref-20181230", CaseStatus.REFERRED, "2018-12-30"))

      givenThereIs(aCaseWith(reference = "ref-20190110", status = REFERRED, createdDate = "2019-01-10"))
      givenThereIs(aStatusChangeWith("ref-20190110", CaseStatus.REFERRED, "2019-01-10"))

      givenThereIs(aCaseWith(reference = "ref-20190203", status = REFERRED, createdDate = "2019-02-03"))
      givenThereIs(aStatusChangeWith("ref-20190203", CaseStatus.REFERRED, "2019-02-03"))

      givenThereIs(aCaseWith(reference = "ref-20190201", status = REFERRED, createdDate = "2019-02-01"))
      givenThereIs(aStatusChangeWith("ref-20190201", CaseStatus.REFERRED, "2019-02-01"))

      givenThereIs(aCaseWith(reference = "completed", status = COMPLETED, createdDate = "2019-02-01"))
      givenThereIs(aStatusChangeWith("completed", CaseStatus.REFERRED, "2019-02-01"))


      When("The job runs")
      result(job.execute(), timeout)

      Then("The Referred Days Elapsed should be correct")
      referredDaysElapsedForCase("ref-20181220") shouldBe 29
      referredDaysElapsedForCase("ref-20181230") shouldBe 24
      referredDaysElapsedForCase("ref-20190110") shouldBe 17
      referredDaysElapsedForCase("ref-20190203") shouldBe 0
      referredDaysElapsedForCase("ref-20190201") shouldBe 1
      referredDaysElapsedForCase("completed") shouldBe -1 // Unchanged
    }

    scenario("Calculates elapsed days for SUSPENDED cases") {
      Given("There are cases with mixed statuses in the database")

      givenThereIs(aCaseWith(reference = "s-ref-20181220", status = SUSPENDED, createdDate = "2018-12-20"))
      givenThereIs(aStatusChangeWith("s-ref-20181220", CaseStatus.SUSPENDED, "2018-12-20"))

      givenThereIs(aCaseWith(reference = "s-ref-20181230", status = SUSPENDED, createdDate = "2018-12-30"))
      givenThereIs(aStatusChangeWith("s-ref-20181230", CaseStatus.SUSPENDED, "2018-12-30"))

      givenThereIs(aCaseWith(reference = "s-ref-20190110", status = SUSPENDED, createdDate = "2019-01-10"))
      givenThereIs(aStatusChangeWith("s-ref-20190110", CaseStatus.SUSPENDED, "2019-01-10"))

      givenThereIs(aCaseWith(reference = "s-ref-20190203", status = SUSPENDED, createdDate = "2019-02-03"))
      givenThereIs(aStatusChangeWith("s-ref-20190203", CaseStatus.SUSPENDED, "2019-02-03"))

      givenThereIs(aCaseWith(reference = "s-ref-20190201", status = SUSPENDED, createdDate = "2019-02-01"))
      givenThereIs(aStatusChangeWith("s-ref-20190201", CaseStatus.SUSPENDED, "2019-02-01"))

      givenThereIs(aCaseWith(reference = "s-completed", status = COMPLETED, createdDate = "2019-02-01"))
      givenThereIs(aStatusChangeWith("s-completed", CaseStatus.SUSPENDED, "2019-02-01"))


      When("The job runs")
      result(job.execute(), timeout)

      Then("The Referred Days Elapsed should be correct")
      referredDaysElapsedForCase("s-ref-20181220") shouldBe 29
      referredDaysElapsedForCase("s-ref-20181230") shouldBe 24
      referredDaysElapsedForCase("s-ref-20190110") shouldBe 17
      referredDaysElapsedForCase("s-ref-20190203") shouldBe 0
      referredDaysElapsedForCase("s-ref-20190201") shouldBe 1
      referredDaysElapsedForCase("s-completed") shouldBe -1 // Unchanged
    }
  }


  private def toInstant (date : String) = {
    LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC)
  }

  private def aCaseWith(reference: String, createdDate: String, status: CaseStatus): Case = {
    createCase(app = createBasicBTIApplication).copy(
      reference = reference,
      createdDate = LocalDate.parse(createdDate).atStartOfDay().toInstant(ZoneOffset.UTC),
      status = status,
      referredDaysElapsed = -1
    )
  }

  private def aStatusChangeWith(caseReference: String, status: CaseStatus, date: String): Event = {
    EventData.createCaseStatusChangeEvent(caseReference, from = OPEN, to = status)
      .copy(timestamp = toInstant(date))
  }

  private def givenThereIs(c: Case): Unit = storeCases(c)
  private def givenThereIs(c: Event): Unit = storeEvents(c)

  private def referredDaysElapsedForCase : String => Long = { reference => getCase(reference).map(_.referredDaysElapsed).getOrElse(0)}

}
