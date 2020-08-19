/*
 * Copyright 2020 HM Revenue & Customs
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

import play.api.libs.json._
import uk.gov.hmrc.bindingtariffclassification.utils.JsonUtil
import uk.gov.hmrc.play.json.Union

object MongoFormatters {

  implicit val formatInstant: OFormat[Instant] = new OFormat[Instant] {
    override def writes(datetime: Instant): JsObject = {
      Json.obj("$date" -> datetime.toEpochMilli)
    }

    override def reads(json: JsValue): JsResult[Instant] = {
      json match {
        case JsObject(map) if map.contains("$date") =>
          map("$date") match {
            case JsNumber(v) => JsSuccess(Instant.ofEpochMilli(v.toLong))
            case _ => JsError("Unexpected Instant Format")
          }
        case _ => JsError("Unexpected Instant Format")
      }
    }
  }

  // `Sequence` formatters
  implicit val formatSequence: OFormat[Sequence] = Json.format[Sequence]

  // `Case` formatters
  implicit val formatRepaymentClaim: OFormat[RepaymentClaim] = Json.format[RepaymentClaim]
  implicit val formatAddress: OFormat[Address] = Json.format[Address]
  implicit val formatTraderContactDetails: OFormat[TraderContactDetails] = Json.format[TraderContactDetails]

  implicit val formatOperator: OFormat[Operator] = Json.format[Operator]
  implicit val formatCaseStatus: Format[CaseStatus.Value] = EnumJson.format(CaseStatus)
  implicit val formatPseudoCaseStatus: Format[PseudoCaseStatus.Value] = EnumJson.format(PseudoCaseStatus)
  implicit val formatAppealStatus: Format[AppealStatus.Value] = EnumJson.format(AppealStatus)
  implicit val formatAppealType: Format[AppealType.Value] = EnumJson.format(AppealType)
  implicit val formatSampleStatus: Format[SampleStatus.Value] = EnumJson.format(SampleStatus)
  implicit val formatSampleReturn: Format[SampleReturn.Value] = EnumJson.format(SampleReturn)
  implicit val formatCancelReason: Format[CancelReason.Value] = EnumJson.format(CancelReason)
  implicit val formatReferralReason: Format[ReferralReason.Value] = EnumJson.format(ReferralReason)
  implicit val formatApplicationType: Format[ApplicationType.Value] = EnumJson.format(ApplicationType)
  implicit val formatLiabilityStatus: Format[LiabilityStatus.Value] = EnumJson.format(LiabilityStatus)
  implicit val formatAttachment: OFormat[Attachment] = Json.format[Attachment]
  implicit val formatEORIDetails: OFormat[EORIDetails] = Json.format[EORIDetails]
  implicit val formatAgentDetails: OFormat[AgentDetails] = Json.format[AgentDetails]
  implicit val formatContact: OFormat[Contact] = Json.format[Contact]

  implicit val formatImportExport: Format[ImportExport.Value] = EnumJson.format(ImportExport)

  implicit val formatLiabilityOrder: OFormat[LiabilityOrder] = Json.format[LiabilityOrder]
  implicit val formatBTIApplication: OFormat[BTIApplication] = Json.format[BTIApplication]
  implicit val formatApplication: Format[Application] = Union.from[Application]("type")
    .and[BTIApplication](ApplicationType.BTI.toString)
    .and[LiabilityOrder](ApplicationType.LIABILITY_ORDER.toString)
    .format

  implicit val formatAppeal: OFormat[Appeal] = Json.format[Appeal]
  implicit val formatCancellation: OFormat[Cancellation] = Json.format[Cancellation]
  implicit val formatDecision: OFormat[Decision] = Json.format[Decision]
  implicit val formatSample: OFormat[Sample] = Json.format[Sample]
  implicit val formatCase: OFormat[Case] = JsonUtil.convertToOFormat(Json.using[Json.WithDefaultValues].format[Case])

  // `Event` formatters
  implicit val formatCaseStatusChange: OFormat[CaseStatusChange] = Json.format[CaseStatusChange]
  implicit val formatCancellationCaseStatusChange: OFormat[CancellationCaseStatusChange] = Json.format[CancellationCaseStatusChange]
  implicit val formatReferralCaseStatusChange: OFormat[ReferralCaseStatusChange] = Json.format[ReferralCaseStatusChange]
  implicit val formatCompletedCaseStatusChange: OFormat[CompletedCaseStatusChange] = Json.format[CompletedCaseStatusChange]
  implicit val formatAppealStatusChange: OFormat[AppealStatusChange] = Json.format[AppealStatusChange]
  implicit val formatAppealAdded: OFormat[AppealAdded] = Json.format[AppealAdded]
  implicit val formatSampleStatusChange: OFormat[SampleStatusChange] = Json.format[SampleStatusChange]
  implicit val formatSampleReturnChange: OFormat[SampleReturnChange] = Json.format[SampleReturnChange]
  implicit val formatExtendedUseStatusChange: OFormat[ExtendedUseStatusChange] = Json.format[ExtendedUseStatusChange]
  implicit val formatAssignmentChange: OFormat[AssignmentChange] = Json.format[AssignmentChange]
  implicit val formatQueueChange: OFormat[QueueChange] = Json.format[QueueChange]
  implicit val formatNote: OFormat[Note] = Json.format[Note]
  implicit val formatCaseCreated: OFormat[CaseCreated] = Json.format[CaseCreated]

  implicit val formatEventDetail: Format[Details] = Union.from[Details]("type")
    .and[CaseStatusChange](EventType.CASE_STATUS_CHANGE.toString)
    .and[CancellationCaseStatusChange](EventType.CASE_CANCELLATION.toString)
    .and[ReferralCaseStatusChange](EventType.CASE_REFERRAL.toString)
    .and[CompletedCaseStatusChange](EventType.CASE_COMPLETED.toString)
    .and[AppealStatusChange](EventType.APPEAL_STATUS_CHANGE.toString)
    .and[AppealAdded](EventType.APPEAL_ADDED.toString)
    .and[SampleStatusChange](EventType.SAMPLE_STATUS_CHANGE.toString)
    .and[SampleReturnChange](EventType.SAMPLE_RETURN_CHANGE.toString)
    .and[ExtendedUseStatusChange](EventType.EXTENDED_USE_STATUS_CHANGE.toString)
    .and[AssignmentChange](EventType.ASSIGNMENT_CHANGE.toString)
    .and[QueueChange](EventType.QUEUE_CHANGE.toString)
    .and[Note](EventType.NOTE.toString)
    .and[CaseCreated](EventType.CASE_CREATED.toString)
    .format


  implicit val formatEventType: Format[EventType.Value] = EnumJson.format(EventType)
  implicit val formatEvent: OFormat[Event] = Json.format[Event]
  implicit val formatSchedulerRunEvent: OFormat[SchedulerRunEvent] = Json.format[SchedulerRunEvent]

}
