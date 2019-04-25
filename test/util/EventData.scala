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

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.UUID

import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.{CaseStatus, _}
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.utils.RandomGenerator

object EventData {

  private def createEvent(caseRef: String, details: Details, date: Instant = Instant.now()): Event = {
    Event(
      id = RandomGenerator.randomUUID(),
      details = details,
      operator = Operator(RandomGenerator.randomUUID(), Some("user name")),
      caseReference = caseRef,
      timestamp = date
    )
  }

  def createNoteEvent(caseReference: String, date: Instant = Instant.now()): Event = {
    createEvent(
      caseRef = caseReference,
      details = Note("This is a random note"),
      date = date
    )
  }

  def createCaseStatusChangeEvent(caseReference: String, from: CaseStatus = DRAFT, to : CaseStatus = NEW, date: Instant = Instant.now()): Event = {
    createEvent(
      caseRef = caseReference,
      details = CaseStatusChange(from = from, to = to, comment = Some("comment")),
      date = date
    )
  }

  def createAppealStatusChangeEvent(caseReference: String): Event = {
    createEvent(
      caseRef = caseReference,
      details = AppealStatusChange(from = Some(AppealStatus.ALLOWED), to = Some(AppealStatus.IN_PROGRESS), comment = Some("comment"))
    )
  }

  def createReviewStatusChangeEvent(caseReference: String): Event = {
    createEvent(
      caseRef = caseReference,
      details = ReviewStatusChange(from = Some(ReviewStatus.IN_PROGRESS), to = Some(ReviewStatus.OVERTURNED), comment = Some("comment"))
    )
  }

  def createExtendedUseStatusChangeEvent(caseReference: String): Event = {
    createEvent(
      caseRef = caseReference,
      details = ExtendedUseStatusChange(from = true, to = false, comment = Some("comment"))
    )
  }

  def createQueueChangeEvent(caseReference: String): Event = {
    createEvent(
      caseRef = caseReference,
      details = QueueChange(from = Some("q1"), to = Some("q2"), comment = Some("comment"))
    )
  }

  def createAssignmentChangeEvent(caseReference: String): Event = {
    val o1 = Operator(RandomGenerator.randomUUID(), Some("user 1"))
    val o2 = Operator(RandomGenerator.randomUUID(), Some("user 2"))

    createEvent(
      caseRef = caseReference,
      details = AssignmentChange(from = Some(o1), to = Some(o2), comment = Some("comment"))
    )
  }

  def anEvent(withModifier: (Event => Event)*): Event = {
    val example = createCaseStatusChangeEvent(UUID.randomUUID().toString)

    withModifier.foldLeft(example)((current: Event, modifier) => modifier.apply(current))
  }

  def withCaseReference(reference: String): Event => Event = _.copy(caseReference = reference)

  def withStatusChange(from: CaseStatus, to: CaseStatus): Event =>  Event = _.copy(details = CaseStatusChange(from, to))

  def withTimestamp(date: String): Event => Event = _.copy(timestamp = LocalDateTime.parse(date).atOffset(ZoneOffset.UTC).toInstant)

}
