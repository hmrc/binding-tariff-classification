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

package uk.gov.hmrc.bindingtariffclassification.todelete

import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.utils.RandomGenerator

object EventData {

  def createNoteEvent(caseReference: String): Event = {
    Event(
      id = RandomGenerator.randomUUID(),
      details = Note(Some("This is a note")),
      userId = RandomGenerator.randomUUID(),
      caseReference
    )
  }

  def createCaseStatusChangeEvent(caseReference: String): Event = {
    Event(
      id = RandomGenerator.randomUUID(),
      details = CaseStatusChange(from = CaseStatus.DRAFT, to = CaseStatus.NEW),
      userId = RandomGenerator.randomUUID(),
      caseReference
    )
  }

  def createAttachmentEvent(caseReference: String): Event = {
    Event(
      id = RandomGenerator.randomUUID(),
      details = Attachment(url = "files://aws.bucket/12345", mimeType = "media/jpg"),
      userId = RandomGenerator.randomUUID(),
      caseReference
    )
  }

}
