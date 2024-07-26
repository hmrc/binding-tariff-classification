/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.component.jobs

import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.bindingtariffclassification.component.utils.{AppConfigWithAFixedDate, IntegrationSpecBase}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.metrics.HasMetrics
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus._
import uk.gov.hmrc.bindingtariffclassification.model.{Case, CaseStatus, Event}
import uk.gov.hmrc.bindingtariffclassification.scheduler.ReferredDaysElapsedJob
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import util.CaseData._
import util.{EventData, TestMetrics}

import java.time._
import scala.concurrent.Await.result

class ReferredDaysElapsedJobISpec extends IntegrationSpecBase {

  protected val serviceUrl = s"http://localhost:$port"

  override lazy val app: Application = new GuiceApplicationBuilder()
    .bindings(bind[AppConfig].to[AppConfigWithAFixedDate])
    .configure("metrics.enabled" -> false)
    .configure("mongodb.uri" -> "mongodb://localhost:27017/test-ClassificationMongoRepositoryTest")
    .overrides(bind[Metrics].toInstance(new TestMetrics))
    .overrides(bind[HasMetrics].toInstance(FakeHasMetrics))
    .overrides(bind[HttpClientV2].toInstance(httpClientV2))
    .build()

  private val job: ReferredDaysElapsedJob = app.injector.instanceOf[ReferredDaysElapsedJob]

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

  "ReferredDaysElapsedJob" when {

    "There are cases with mixed statuses in the database, Referred Days Elapsed Job" should {
      "calculates elapsed days for REFERRED cases" in {

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

        result(job.execute(), timeout)

        referredDaysElapsedForCase("ref-20181220") shouldBe 29
        referredDaysElapsedForCase("ref-20181230") shouldBe 24
        referredDaysElapsedForCase("ref-20190110") shouldBe 17
        referredDaysElapsedForCase("ref-20190203") shouldBe 0
        referredDaysElapsedForCase("ref-20190201") shouldBe 1
        referredDaysElapsedForCase("completed")    shouldBe -1 // Unchanged

      }
    }

    "There are cases with mixed statuses in the database, Referred Days Elapsed Job" should {
      "calculates elapsed days for SUSPENDED cases" in {

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

        result(job.execute(), timeout)

        referredDaysElapsedForCase("ref-20181220") shouldBe 29
        referredDaysElapsedForCase("ref-20181230") shouldBe 24
        referredDaysElapsedForCase("ref-20190110") shouldBe 17
        referredDaysElapsedForCase("ref-20190203") shouldBe 0
        referredDaysElapsedForCase("ref-20190201") shouldBe 1
        referredDaysElapsedForCase("completed")    shouldBe -1 // Unchanged

      }
    }
  }
}
