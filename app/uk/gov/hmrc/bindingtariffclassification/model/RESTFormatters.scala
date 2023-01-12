/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.data.NonEmptySeq
import play.api.libs.json._
import uk.gov.hmrc.bindingtariffclassification.model.reporting._
import uk.gov.hmrc.play.json.Union

object RESTFormatters {
  // NonEmpty formatters
  implicit def formatNonEmptySeq[A: Format]: Format[NonEmptySeq[A]] = Format(
    Reads.list[A].filter(JsonValidationError("error.empty"))(_.nonEmpty).map(NonEmptySeq.fromSeqUnsafe(_)),
    Writes.seq[A].contramap(_.toSeq)
  )

  // User formatters
  implicit val formatApplicationType: Format[ApplicationType.Value] = EnumJson.format(ApplicationType)
  implicit val role: Format[Role.Value]                             = EnumJson.format(Role)
  implicit val formatOperator: OFormat[Operator]                    = Json.using[Json.WithDefaultValues].format[Operator]

  implicit val formatKeyword: OFormat[Keyword] = Json.using[Json.WithDefaultValues].format[Keyword]
  // `Case` formatters
  implicit val formatRepaymentClaim: OFormat[RepaymentClaim]             = Json.format[RepaymentClaim]
  implicit val formatAddress: OFormat[Address]                           = Json.format[Address]
  implicit val formatTraderContactDetails: OFormat[TraderContactDetails] = Json.format[TraderContactDetails]

  implicit val formatCaseStatus: Format[CaseStatus.Value]             = EnumJson.format(CaseStatus)
  implicit val formatPseudoCaseStatus: Format[PseudoCaseStatus.Value] = EnumJson.format(PseudoCaseStatus)
  implicit val formatLiabilityStatus: Format[LiabilityStatus.Value]   = EnumJson.format(LiabilityStatus)
  implicit val formatAppealStatus: Format[AppealStatus.Value]         = EnumJson.format(AppealStatus)
  implicit val formatAppealType: Format[AppealType.Value]             = EnumJson.format(AppealType)
  implicit val formatSampleStatus: Format[SampleStatus.Value]         = EnumJson.format(SampleStatus)
  implicit val formatSampleReturn: Format[SampleReturn.Value]         = EnumJson.format(SampleReturn)
  implicit val formatSampleSend: Format[SampleSend.Value]             = EnumJson.format(SampleSend)
  implicit val formatCancelReason: Format[CancelReason.Value]         = EnumJson.format(CancelReason)
  implicit val formatReferralReason: Format[ReferralReason.Value]     = EnumJson.format(ReferralReason)
  implicit val formatRejectedReason: Format[RejectReason.Value]       = EnumJson.format(RejectReason)
  implicit val miscCaseType: Format[MiscCaseType.Value]               = EnumJson.format(MiscCaseType)

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

  implicit val formatCase: OFormat[Case]               = Json.using[Json.WithDefaultValues].format[Case]
  implicit val formatNewCase: OFormat[NewCaseRequest]  = Json.format[NewCaseRequest]
  implicit val formatCaseHeader: OFormat[CaseHeader]   = Json.format[CaseHeader]
  implicit val formatCaseKeyword: OFormat[CaseKeyword] = Json.format[CaseKeyword]

  // `Event` formatters
  implicit val formatCaseStatusChange: OFormat[CaseStatusChange] = Json.format[CaseStatusChange]
  implicit val formatCancellationCaseStatusChange: OFormat[CancellationCaseStatusChange] =
    Json.format[CancellationCaseStatusChange]
  implicit val formatCompletedCaseStatusChange: OFormat[CompletedCaseStatusChange] =
    Json.format[CompletedCaseStatusChange]
  implicit val formatReferralCaseStatusChange: OFormat[ReferralCaseStatusChange] = Json.format[ReferralCaseStatusChange]
  implicit val formatRejectCaseStatusChange: OFormat[RejectCaseStatusChange]     = Json.format[RejectCaseStatusChange]
  implicit val formatAppealStatusChange: OFormat[AppealStatusChange]             = Json.format[AppealStatusChange]
  implicit val formatAppealAdded: OFormat[AppealAdded]                           = Json.format[AppealAdded]
  implicit val formatSampleStatusChange: OFormat[SampleStatusChange]             = Json.format[SampleStatusChange]
  implicit val formatSampleReturnChange: OFormat[SampleReturnChange]             = Json.format[SampleReturnChange]
  implicit val formatSampleSendChange: OFormat[SampleSendChange]                 = Json.format[SampleSendChange]
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
    .and[RejectCaseStatusChange](EventType.CASE_REJECTED.toString)
    .and[AppealStatusChange](EventType.APPEAL_STATUS_CHANGE.toString)
    .and[AppealAdded](EventType.APPEAL_ADDED.toString)
    .and[SampleStatusChange](EventType.SAMPLE_STATUS_CHANGE.toString)
    .and[SampleReturnChange](EventType.SAMPLE_RETURN_CHANGE.toString)
    .and[SampleSendChange](EventType.SAMPLE_SEND_CHANGE.toString)
    .and[ExtendedUseStatusChange](EventType.EXTENDED_USE_STATUS_CHANGE.toString)
    .and[AssignmentChange](EventType.ASSIGNMENT_CHANGE.toString)
    .and[QueueChange](EventType.QUEUE_CHANGE.toString)
    .and[Note](EventType.NOTE.toString)
    .and[CaseCreated](EventType.CASE_CREATED.toString)
    .and[ExpertAdviceReceived](EventType.EXPERT_ADVICE_RECEIVED.toString)
    .format

  implicit val formatEvent: OFormat[Event]                         = Json.format[Event]
  implicit val formatNewEventRequest: OFormat[NewEventRequest]     = Json.format[NewEventRequest]
  implicit val formatNewUserRequest: OFormat[NewUserRequest]       = Json.format[NewUserRequest]
  implicit val formatNewKeywordRequest: OFormat[NewKeywordRequest] = Json.format[NewKeywordRequest]

  implicit val formatBankHoliday: OFormat[BankHoliday]                   = Json.format[BankHoliday]
  implicit val formatBankHolidaysSet: OFormat[BankHolidaySet]            = Json.format[BankHolidaySet]
  implicit val formatBankHolidaysResponse: OFormat[BankHolidaysResponse] = Json.format[BankHolidaysResponse]

  // `Update` formatters
  implicit def formatSetValue[A: Format]: OFormat[SetValue[A]] = Json.format[SetValue[A]]
  implicit val formatNoChange: OFormat[NoChange.type]          = Json.format[NoChange.type]

  implicit def formatUpdate[A: Format]: Format[Update[A]] =
    Union
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

  implicit val formatNumberField: OFormat[NumberField]                   = Json.format[NumberField]
  implicit val formatStatusField: OFormat[StatusField]                   = Json.format[StatusField]
  implicit val formatLiabilityStatusField: OFormat[LiabilityStatusField] = Json.format[LiabilityStatusField]
  implicit val formatCaseTypeField: OFormat[CaseTypeField]               = Json.format[CaseTypeField]
  implicit val formatChapterField: OFormat[ChapterField]                 = Json.format[ChapterField]
  implicit val formatDateField: OFormat[DateField]                       = Json.format[DateField]
  implicit val formatStringField: OFormat[StringField]                   = Json.format[StringField]
  implicit val formatDaysSinceField: OFormat[DaysSinceField]             = Json.format[DaysSinceField]

  implicit val formatReportField: Format[ReportField[_]] = Union
    .from[ReportField[_]]("type")
    .and[NumberField](ReportFieldType.Number.name)
    .and[StatusField](ReportFieldType.Status.name)
    .and[LiabilityStatusField](ReportFieldType.LiabilityStatus.name)
    .and[CaseTypeField](ReportFieldType.CaseType.name)
    .and[ChapterField](ReportFieldType.Chapter.name)
    .and[DateField](ReportFieldType.Date.name)
    .and[StringField](ReportFieldType.String.name)
    .and[DaysSinceField](ReportFieldType.DaysSince.name)
    .format

  implicit val formatNumberResultField: OFormat[NumberResultField] = Json.format[NumberResultField]
  implicit val formatStatusResultField: OFormat[StatusResultField] = Json.format[StatusResultField]
  implicit val formatLiabilityStatusResultField: OFormat[LiabilityStatusResultField] =
    Json.format[LiabilityStatusResultField]
  implicit val formatCaseTypeResultField: OFormat[CaseTypeResultField] = Json.format[CaseTypeResultField]
  implicit val formatDateResultField: OFormat[DateResultField]         = Json.format[DateResultField]
  implicit val formatStringResultField: OFormat[StringResultField]     = Json.format[StringResultField]

  implicit val formatReportResultField: Format[ReportResultField[_]] = Union
    .from[ReportResultField[_]]("type")
    .and[NumberResultField](ReportFieldType.Number.name)
    .and[StatusResultField](ReportFieldType.Status.name)
    .and[LiabilityStatusResultField](ReportFieldType.LiabilityStatus.name)
    .and[CaseTypeResultField](ReportFieldType.CaseType.name)
    .and[DateResultField](ReportFieldType.Date.name)
    .and[StringResultField](ReportFieldType.String.name)
    .format

  implicit val formatSimpleResultGroup: OFormat[SimpleResultGroup] = Json.format[SimpleResultGroup]
  implicit val formatCaseResultGroup: OFormat[CaseResultGroup]     = Json.format[CaseResultGroup]

  implicit val readResultGroup: Reads[ResultGroup] =
    (__ \ "cases").readNullable[List[Case]].flatMap {
      case Some(_) => formatCaseResultGroup.widen[ResultGroup]
      case None    => formatSimpleResultGroup.widen[ResultGroup]
    }

  implicit val writeResultGroup: OWrites[ResultGroup] = OWrites[ResultGroup] {
    case caseResult: CaseResultGroup     => formatCaseResultGroup.writes(caseResult)
    case simpleResult: SimpleResultGroup => formatSimpleResultGroup.writes(simpleResult)
  }

  implicit val formatResultGroup: OFormat[ResultGroup] = OFormat(readResultGroup, writeResultGroup)

  implicit val formatQueueResultGroup: OFormat[QueueResultGroup] = Json.format[QueueResultGroup]
}
