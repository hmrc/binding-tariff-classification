/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.repository

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.bindingtariffclassification.model._
import java.time.Instant

class UpdateMapperSpec extends AnyWordSpec with Matchers {

  private val mapper = new UpdateMapper()

  "updateApplication" should {

    "handle BTIUpdate with applicationPdf" in {
      val attachment = Attachment("id", public = true, None, Instant.now(), None)
      val update     = BTIUpdate(applicationPdf = SetValue(Some(attachment)))

      val result = mapper.updateApplication(update)

      result           should not be empty
      result.head._1 shouldBe "application.applicationPdf"
    }

    "handle BTIUpdate with None applicationPdf" in {
      val update = BTIUpdate(applicationPdf = SetValue(None))

      val result = mapper.updateApplication(update)

      result should not be empty
    }

    "handle LiabilityUpdate with traderName" in {
      val update = LiabilityUpdate(traderName = SetValue("New Trader"))

      val result = mapper.updateApplication(update)

      result           should not be empty
      result.head._1 shouldBe "application.traderName"
    }
  }

  "updateCase" should {

    "handle CaseUpdate with application" in {
      val update = CaseUpdate(application = Some(LiabilityUpdate(traderName = SetValue("Test"))))

      val result = mapper.updateCase(update)

      result should not be null
    }

    "handle CaseUpdate without application" in {
      val update = CaseUpdate(application = None)

      val result = mapper.updateCase(update)

      result should not be null
    }
  }
}
