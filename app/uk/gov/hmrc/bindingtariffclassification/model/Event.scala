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

package uk.gov.hmrc.bindingtariffclassification.model

import java.time.Instant
import java.util.UUID

import uk.gov.hmrc.bindingtariffclassification.model.AppealStatus.AppealStatus
import uk.gov.hmrc.bindingtariffclassification.model.AppealType.AppealType
import uk.gov.hmrc.bindingtariffclassification.model.CancelReason.CancelReason
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.EventType.EventType
import uk.gov.hmrc.bindingtariffclassification.model.ReferralReason.ReferralReason
import uk.gov.hmrc.bindingtariffclassification.model.RejectReason.RejectReason
import uk.gov.hmrc.bindingtariffclassification.model.SampleReturn.SampleReturn
import uk.gov.hmrc.bindingtariffclassification.model.SampleSend.SampleSend
import uk.gov.hmrc.bindingtariffclassification.model.SampleStatus.SampleStatus

case class Event(
  id: String = UUID.randomUUID().toString,
  details: Details,
  operator: Operator,
  caseReference: String,
  timestamp: Instant
)

sealed trait Details {
  val `type`: EventType
}

sealed trait OptionalComment {
  val comment: Option[String]
}

sealed trait OptionalAttachment {
  val attachmentId: Option[String]
}

sealed trait FieldChange[T] extends Details with OptionalComment {
  val from: T
  val to: T
  val comment: Option[String]
}

case class CaseStatusChange(
  override val from: CaseStatus,
  override val to: CaseStatus,
  override val comment: Option[String] = None,
  override val attachmentId: Option[String] = None
) extends FieldChange[CaseStatus]
    with OptionalAttachment {
  override val `type`: EventType.Value = EventType.CASE_STATUS_CHANGE
}

case class RejectCaseStatusChange(
  override val from: CaseStatus,
  override val to: CaseStatus,
  override val comment: Option[String] = None,
  override val attachmentId: Option[String] = None,
  reason: RejectReason
) extends FieldChange[CaseStatus]
    with OptionalAttachment {
  override val `type`: EventType.Value = EventType.CASE_REJECTED
}

case class CancellationCaseStatusChange(
  override val from: CaseStatus,
  override val comment: Option[String] = None,
  override val attachmentId: Option[String] = None,
  reason: CancelReason
) extends FieldChange[CaseStatus]
    with OptionalAttachment {
  override val to: CaseStatus          = CaseStatus.CANCELLED
  override val `type`: EventType.Value = EventType.CASE_CANCELLATION
}

case class ReferralCaseStatusChange(
  override val from: CaseStatus,
  override val comment: Option[String] = None,
  override val attachmentId: Option[String] = None,
  referredTo: String,
  reason: Seq[ReferralReason]
) extends FieldChange[CaseStatus]
    with OptionalAttachment {
  override val to: CaseStatus          = CaseStatus.REFERRED
  override val `type`: EventType.Value = EventType.CASE_REFERRAL
}

case class CompletedCaseStatusChange(
  override val from: CaseStatus,
  override val comment: Option[String] = None,
  email: Option[String]
) extends FieldChange[CaseStatus] {
  override val to: CaseStatus          = CaseStatus.COMPLETED
  override val `type`: EventType.Value = EventType.CASE_COMPLETED
}

case class CaseCreated(
  comment: String
) extends Details {
  override val `type`: EventType = EventType.CASE_CREATED
}

case class AppealAdded(
  appealType: AppealType,
  appealStatus: AppealStatus,
  override val comment: Option[String] = None
) extends Details
    with OptionalComment {
  override val `type`: EventType.Value = EventType.APPEAL_ADDED
}

case class AppealStatusChange(
  appealType: AppealType,
  override val from: AppealStatus,
  override val to: AppealStatus,
  override val comment: Option[String] = None
) extends FieldChange[AppealStatus] {
  override val `type`: EventType.Value = EventType.APPEAL_STATUS_CHANGE
}

case class ExtendedUseStatusChange(
  override val from: Boolean,
  override val to: Boolean,
  override val comment: Option[String] = None
) extends FieldChange[Boolean] {
  override val `type`: EventType.Value = EventType.EXTENDED_USE_STATUS_CHANGE
}

case class AssignmentChange(
  override val from: Option[Operator],
  override val to: Option[Operator],
  override val comment: Option[String] = None
) extends FieldChange[Option[Operator]] {
  override val `type`: EventType.Value = EventType.ASSIGNMENT_CHANGE
}

case class QueueChange(
  override val from: Option[String],
  override val to: Option[String],
  override val comment: Option[String] = None
) extends FieldChange[Option[String]] {
  override val `type`: EventType.Value = EventType.QUEUE_CHANGE
}

case class Note(
  comment: String
) extends Details {
  override val `type`: EventType.Value = EventType.NOTE
}

case class SampleStatusChange(
  override val from: Option[SampleStatus],
  override val to: Option[SampleStatus],
  override val comment: Option[String] = None
) extends FieldChange[Option[SampleStatus]] {
  override val `type`: EventType.Value = EventType.SAMPLE_STATUS_CHANGE
}

case class SampleReturnChange(
  override val from: Option[SampleReturn],
  override val to: Option[SampleReturn],
  override val comment: Option[String] = None
) extends FieldChange[Option[SampleReturn]] {
  override val `type`: EventType.Value = EventType.SAMPLE_RETURN_CHANGE
}

case class SampleSendChange(
  override val from: Option[SampleSend],
  override val to: Option[SampleSend],
  override val comment: Option[String] = None
) extends FieldChange[Option[SampleSend]] {
  override val `type`: EventType.Value = EventType.SAMPLE_SEND_CHANGE
}

case class ExpertAdviceReceived(
  comment: String
) extends Details {
  override val `type`: EventType.Value = EventType.EXPERT_ADVICE_RECEIVED
}

object EventType extends Enumeration {
  type EventType = Value
  val CASE_STATUS_CHANGE         = Value
  val CASE_REFERRAL              = Value
  val CASE_REJECTED              = Value
  val CASE_COMPLETED             = Value
  val CASE_CANCELLATION          = Value
  val APPEAL_STATUS_CHANGE       = Value
  val APPEAL_ADDED               = Value
  val EXTENDED_USE_STATUS_CHANGE = Value
  val ASSIGNMENT_CHANGE          = Value
  val QUEUE_CHANGE               = Value
  val NOTE                       = Value
  val SAMPLE_STATUS_CHANGE       = Value
  val SAMPLE_RETURN_CHANGE       = Value
  val SAMPLE_SEND_CHANGE         = Value
  val CASE_CREATED               = Value
  val EXPERT_ADVICE_RECEIVED     = Value
}
