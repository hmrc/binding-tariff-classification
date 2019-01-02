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

package uk.gov.hmrc.component

import uk.gov.hmrc.bindingtariffclassification.model.{Case, CaseStatus}
import uk.gov.hmrc.bindingtariffclassification.scheduler.DaysElapsedJob
import util.CaseData._

import scala.concurrent.Await.result

class DaysElapsedSpec extends BaseFeatureSpec {

  override lazy val port = 14683
  protected val serviceUrl = s"http://localhost:$port"

  private val c: Case = createCase(app = createBasicBTIApplication)

  private val job: DaysElapsedJob = app.injector.instanceOf[DaysElapsedJob]

  // TODO: call the endpoint

  feature("Days Elapsed Job") {

    scenario("Updates Cases with status NEW and OPEN") {
      Given("There is cases with mixed statuses in the database")
      val newCase = c.copy(reference = "new", status = CaseStatus.NEW, daysElapsed = 0)
      val openCase = c.copy(reference = "open", status = CaseStatus.OPEN, daysElapsed = 0)
      val otherCase = c.copy(reference = "other", status = CaseStatus.SUSPENDED, daysElapsed = 0)
      storeCases(newCase, openCase, otherCase)

      When("The job runs")
      result(job.execute(), timeout)

      Then("NEW cases should have elapsed 1 day")
      getCase("new").map(_.daysElapsed) shouldBe Some(1)

      Then("OPEN cases should have elapsed 1 day")
      getCase("open").map(_.daysElapsed) shouldBe Some(1)

      Then("Other cases should remain the same")
      getCase("other").map(_.daysElapsed) shouldBe Some(0)
    }

  }

}
