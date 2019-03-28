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

import org.scalatest.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.bindingtariffclassification.component.utils.MockAppConfig
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.{Case, CaseStatus}
import uk.gov.hmrc.bindingtariffclassification.scheduler.DaysElapsedJob
import util.CaseData._

import scala.concurrent.Await.result

class DaysElapsedSpec extends BaseFeatureSpec with MockitoSugar {

  override lazy val port = 14683
  protected val serviceUrl = s"http://localhost:$port"

  private val injector = new GuiceApplicationBuilder()
    .bindings(bind[AppConfig].to[MockAppConfig])
    .disable[com.kenshoo.play.metrics.PlayModule]
    .configure("metrics.enabled" -> false)
    .injector()


  private val job: DaysElapsedJob = injector.instanceOf[DaysElapsedJob]

  feature("Days Elapsed Job") {
    scenario("Updates Cases with status NEW and OPEN") {
      Given("There are cases with mixed statuses in the database")
      givenThereIs(aCaseWith(reference = "ref-20181220", status = CaseStatus.OPEN, createdDate = "2018-12-20"))
      givenThereIs(aCaseWith(reference = "ref-20181230", status = CaseStatus.OPEN, createdDate = "2018-12-30"))
      givenThereIs(aCaseWith(reference = "ref-20190110", status = CaseStatus.OPEN, createdDate = "2019-01-10"))

      When("The job runs")
      result(job.execute(), timeout)

      Then("The days elapsed field is incremented appropriately")
      daysElapsedForCase(reference = "ref-20181220") shouldBe 6 //
      daysElapsedForCase(reference = "ref-20181230") shouldBe 1 //???
      daysElapsedForCase(reference = "ref-20190110") shouldBe 16 // OK!
    }
  }

  private def aCaseWith(reference: String, createdDate: String, status: CaseStatus): Case = {
    createCase(app = createBasicBTIApplication).copy(
      reference = reference,
      createdDate = LocalDate.parse(createdDate).atStartOfDay().toInstant(ZoneOffset.UTC),
      status = status
    )
  }

  private def givenThereIs(c: Case): Unit = storeCases(c)

  private def daysElapsedForCase(reference: String) = {
    getCase(reference).map(_.daysElapsed).getOrElse(0)
  }

}
