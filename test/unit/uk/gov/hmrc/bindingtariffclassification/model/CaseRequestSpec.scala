/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.model

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import util.CaseData
import util.Matchers.roughlyBe

import java.time.Instant

class CaseRequestSpec extends BaseSpec with MockitoSugar {

  private val application = mock[Application]
  private val attachments = mock[Seq[Attachment]]

  "To Case" should {

    "Convert NewCaseRequest To A Case" in {
      when(application.asBTI).thenReturn(CaseData.createBasicBTIApplication)
      val c = NewCaseRequest(application, attachments).toCase("reference")
      c.status               shouldBe CaseStatus.NEW
      c.createdDate          should roughlyBe(Instant.now())
      c.daysElapsed          shouldBe 0
      c.assignee             shouldBe None
      c.queueId              shouldBe None
      c.caseBoardsFileNumber shouldBe None
      c.decision             shouldBe None
      c.application          shouldBe application
      c.attachments          shouldBe attachments
      c.sample.status        shouldBe None
    }

    "Convert NewCaseRequest To A Case with sample provided" in {
      when(application.isBTI).thenReturn(true)
      when(application.asBTI).thenReturn(CaseData.createBTIApplicationWithAllFields())
      val c = NewCaseRequest(application, attachments).toCase("reference")
      c.sample.status shouldBe Some(SampleStatus.AWAITING)
    }
  }

}
