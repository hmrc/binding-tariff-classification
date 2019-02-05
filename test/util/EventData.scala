/*
 * Copyright 2019 HM Revenue & Customs
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

package util

import java.time.Instant

import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.utils.RandomGenerator

object EventData {


  def createEvent(caseReference: String, date :  Instant): Event = {
    Event(
      id = RandomGenerator.randomUUID(),
      details = Note(Some("This is a random note")),
      operator = Operator(RandomGenerator.randomUUID(), Some("user name")),
      caseReference = caseReference,
      timestamp = date
    )
  }

  def createNoteEvent(caseReference: String): Event = {
    Event(
      id = RandomGenerator.randomUUID(),
      details = Note(Some("This is a note")),
      operator = Operator(RandomGenerator.randomUUID(), Some("user name")),
      caseReference = caseReference,
      timestamp = Instant.now()
    )
  }

  def createCaseStatusChangeEvent(caseReference: String): Event = {
    Event(
      id = RandomGenerator.randomUUID(),
      details = CaseStatusChange(from = CaseStatus.DRAFT, to = CaseStatus.NEW),
      operator = Operator(RandomGenerator.randomUUID(), Some("user name")),
      caseReference = caseReference,
      timestamp = Instant.now()
    )
  }

}
