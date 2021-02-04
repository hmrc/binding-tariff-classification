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

import play.api.libs.json._
import uk.gov.hmrc.bindingtariffclassification.model.reporting.{CaseReportGroup, ReportResult}
import uk.gov.hmrc.play.json.Union

object RESTFormatters {
  // `Case` formatters
  implicit val formatRepaymentClaim: OFormat[RepaymentClaim]             = Json.format[RepaymentClaim]
  implicit val formatAddress: OFormat[Address]                           = Json.format[Address]
  implicit val formatTraderContactDetails: OFormat[TraderContactDetails] = Json.format[TraderContactDetails]

  implicit val formatCaseStatus: Format[CaseStatus.Value]           = EnumJson.format(CaseStatus)
  implicit val formatApplicationType: Format[ApplicationType.Value] = EnumJson.format(ApplicationType)
  implicit val formatLiabilityStatus: Format[LiabilityStatus.Value] = EnumJson.format(LiabilityStatus)
  implicit val formatAppealStatus: Format[AppealStatus.Value]       = EnumJson.format(AppealStatus)
  implicit val formatAppealType: Format[AppealType.Value]           = EnumJson.format(AppealType)
  implicit val formatSampleStatus: Format[SampleStatus.Value]       = EnumJson.format(SampleStatus)
  implicit val formatSampleReturn: Format[SampleReturn.Value]       = EnumJson.format(SampleReturn)
  implicit val formatCancelReason: Format[CancelReason.Value]       = EnumJson.format(CancelReason)
  implicit val formatReferralReason: Format[ReferralReason.Value]   = EnumJson.format(ReferralReason)
  implicit val formatCaseReportGroup: Format[CaseReportGroup.Value] = EnumJson.format(CaseReportGroup)
  implicit val miscCaseType: Format[MiscCaseType.Value]             = EnumJson.format(MiscCaseType)

  implicit val formatReportResultMap: OFormat[Map[CaseReportGroup.Value, Option[String]]] = {
    implicit val optrds: Reads[Option[String]] = Reads.optionNoError[String]
    EnumJson.formatMap[CaseReportGroup.Value, Option[String]]
  }

  implicit val formatReportResult: OFormat[ReportResult] = Json.format[ReportResult]

  implicit val formatOperator: OFormat[Operator]         = Json.format[Operator]
  implicit val formatEORIDetails: OFormat[EORIDetails]   = Json.format[EORIDetails]
  implicit val formatAttachment: OFormat[Attachment]     = Json.using[Json.WithDefaultValues].format[Attachment]
  implicit val formatAgentDetails: OFormat[AgentDetails] = Json.format[AgentDetails]
  implicit val formatContact: OFormat[Contact]           = Json.format[Contact]
  implicit val messageLoggedFormat: OFormat[Message]     = Json.format[Message]

  implicit val formatLiabilityOrder: OFormat[LiabilityOrder]            = Json.format[LiabilityOrder]
  implicit val formatBTIApplication: OFormat[BTIApplication]            = Json.using[Json.WithDefaultValues].format[BTIApplication]
  implicit val formatCorrespondence: OFormat[CorrespondenceApplication] = Json.format[CorrespondenceApplication]
  implicit val formatMisc: OFormat[MiscApplication]                     = Json.format[MiscApplication]

  implicit val formatApplication: Format[Application] = Union
    .from[Application]("type")
    .and[BTIApplication](ApplicationType.BTI.toString)
    .and[LiabilityOrder](ApplicationType.LIABILITY_ORDER.toString)
    .and[CorrespondenceApplication](ApplicationType.CORRESPONDENCE.toString)
    .and[MiscApplication](ApplicationType.MISCELLANEOUS.toString)
    .format

  implicit val formatAppeal: OFormat[Appeal]             = Json.format[Appeal]
  implicit val formatCancellation: OFormat[Cancellation] = Json.format[Cancellation]
  implicit val formatDecision: OFormat[Decision]         = Json.format[Decision]
  implicit val formatSample: OFormat[Sample]             = Json.format[Sample]

  implicit val formatCase: OFormat[Case]              = Json.using[Json.WithDefaultValues].format[Case]
  implicit val formatTeam: OFormat[Team]                 = Json.format[Team]
  implicit val formatNewCase: OFormat[NewCaseRequest] = Json.format[NewCaseRequest]
  implicit val formatNewTeam: OFormat[NewTeamRequest] = Json.format[NewTeamRequest]

  // `Event` formatters
  implicit val formatCaseStatusChange: OFormat[CaseStatusChange] = Json.format[CaseStatusChange]
  implicit val formatCancellationCaseStatusChange: OFormat[CancellationCaseStatusChange] =
    Json.format[CancellationCaseStatusChange]
  implicit val formatCompletedCaseStatusChange: OFormat[CompletedCaseStatusChange] =
    Json.format[CompletedCaseStatusChange]
  implicit val formatReferralCaseStatusChange: OFormat[ReferralCaseStatusChange] = Json.format[ReferralCaseStatusChange]
  implicit val formatAppealStatusChange: OFormat[AppealStatusChange]             = Json.format[AppealStatusChange]
  implicit val formatAppealAdded: OFormat[AppealAdded]                           = Json.format[AppealAdded]
  implicit val formatSampleStatusChange: OFormat[SampleStatusChange]             = Json.format[SampleStatusChange]
  implicit val formatSampleReturnChange: OFormat[SampleReturnChange]             = Json.format[SampleReturnChange]
  implicit val formatExtendedUseStatusChange: OFormat[ExtendedUseStatusChange]   = Json.format[ExtendedUseStatusChange]
  implicit val formatAssignmentChange: OFormat[AssignmentChange]                 = Json.format[AssignmentChange]
  implicit val formatQueueChange: OFormat[QueueChange]                           = Json.format[QueueChange]
  implicit val formatCaseCreated: OFormat[CaseCreated]                           = Json.format[CaseCreated]
  implicit val formatExpertAdviceReceived: OFormat[ExpertAdviceReceived]         = Json.format[ExpertAdviceReceived]

  implicit val formatNote: OFormat[Note] = Json.format[Note]

  implicit val formatEventDetail: Format[Details] = Union
    .from[Details]("type")
    .and[CaseStatusChange](EventType.CASE_STATUS_CHANGE.toString)
    .and[CancellationCaseStatusChange](EventType.CASE_CANCELLATION.toString)
    .and[CompletedCaseStatusChange](EventType.CASE_COMPLETED.toString)
    .and[ReferralCaseStatusChange](EventType.CASE_REFERRAL.toString)
    .and[AppealStatusChange](EventType.APPEAL_STATUS_CHANGE.toString)
    .and[AppealAdded](EventType.APPEAL_ADDED.toString)
    .and[SampleStatusChange](EventType.SAMPLE_STATUS_CHANGE.toString)
    .and[SampleReturnChange](EventType.SAMPLE_RETURN_CHANGE.toString)
    .and[ExtendedUseStatusChange](EventType.EXTENDED_USE_STATUS_CHANGE.toString)
    .and[AssignmentChange](EventType.ASSIGNMENT_CHANGE.toString)
    .and[QueueChange](EventType.QUEUE_CHANGE.toString)
    .and[Note](EventType.NOTE.toString)
    .and[CaseCreated](EventType.CASE_CREATED.toString)
    .and[ExpertAdviceReceived](EventType.EXPERT_ADVICE_RECEIVED.toString)
    .format

  implicit val formatEvent: OFormat[Event]                     = Json.format[Event]
  implicit val formatNewEventRequest: OFormat[NewEventRequest] = Json.format[NewEventRequest]

  implicit val formatBankHoliday: OFormat[BankHoliday]                   = Json.format[BankHoliday]
  implicit val formatBankHolidaysSet: OFormat[BankHolidaySet]            = Json.format[BankHolidaySet]
  implicit val formatBankHolidaysResponse: OFormat[BankHolidaysResponse] = Json.format[BankHolidaysResponse]

  // `Update` formatters
  implicit def formatSetValue[A: Format]: OFormat[SetValue[A]] = Json.format[SetValue[A]]
  implicit val formatNoChange: OFormat[NoChange.type] = Json.format[NoChange.type]

  implicit def formatUpdate[A: Format]: Format[Update[A]] = Union
    .from[Update[A]]("type")
    .and[SetValue[A]](UpdateType.SetValue.name)
    .and[NoChange.type](UpdateType.NoChange.name)
    .format

  implicit def formatBtiUpdate: OFormat[BTIUpdate] = {
    implicit def optFormat[A: Format]: Format[Option[A]] = Format(
      Reads.optionNoError[A],
      Writes.optionWithNull[A]
    )
    Json.format[BTIUpdate]
  }

  implicit val formatLiabilityUpdate: OFormat[LiabilityUpdate] = Json.format[LiabilityUpdate]

  implicit val formatApplicationUpdate: Format[ApplicationUpdate] = Union
    .from[ApplicationUpdate]("type")
    .and[BTIUpdate](ApplicationType.BTI.toString)
    .and[LiabilityUpdate](ApplicationType.LIABILITY_ORDER.toString)
    .format

  implicit val formatCaseUpdate: OFormat[CaseUpdate] = Json.format[CaseUpdate]
}
