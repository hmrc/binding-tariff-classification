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

import cats.data.NonEmptySeq
import play.api.libs.json.*
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.reporting.*
import uk.gov.hmrc.bindingtariffclassification.utils.Union
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import uk.gov.hmrc.bindingtariffclassification.model.AppealStatus.AppealStatus
import uk.gov.hmrc.bindingtariffclassification.model.AppealType.AppealType
import uk.gov.hmrc.bindingtariffclassification.model.CancelReason.CancelReason
import uk.gov.hmrc.bindingtariffclassification.model.LiabilityStatus.LiabilityStatus
import uk.gov.hmrc.bindingtariffclassification.model.MiscCaseType.MiscCaseType
import uk.gov.hmrc.bindingtariffclassification.model.ReferralReason.ReferralReason
import uk.gov.hmrc.bindingtariffclassification.model.SampleReturn.SampleReturn
import uk.gov.hmrc.bindingtariffclassification.model.SampleSend.SampleSend
import uk.gov.hmrc.bindingtariffclassification.model.SampleStatus.SampleStatus
import uk.gov.hmrc.bindingtariffclassification.model.RejectReason.RejectReason
import uk.gov.hmrc.bindingtariffclassification.model.Role.Role

import java.time.Instant

object RESTFormatters {
  // NonEmpty formatters
  implicit def formatNonEmptySeq[A: Format]: Format[NonEmptySeq[A]] = Format(
    Reads.list[A].filter(JsonValidationError("error.empty"))(_.nonEmpty).map(NonEmptySeq.fromSeqUnsafe(_)),
    Writes.seq[A].contramap(_.toSeq)
  )

  // User formatters
  implicit val formatApplicationType: Format[ApplicationType.Value] = EnumJson.format(ApplicationType)
  implicit val role: Format[Role.Value]                             = EnumJson.format(Role)

  implicit val formatOperator: Format[Operator] = (
    (JsPath \ "id").format[String] and
      (JsPath \ "name").formatNullable[String] and
      (JsPath \ "email").formatNullable[String] and
      (JsPath \ "role").format[Role] and
      (JsPath \ "memberOfTeams").format[List[String]] and
      (JsPath \ "managerOfTeams").format[List[String]] and
      (JsPath \ "deleted").format[Boolean]
  )(Operator.apply, o => Tuple.fromProductTyped(o))

  implicit val formatKeyword: Format[Keyword] = (
    (JsPath \ "name").format[String] and
      (JsPath \ "approved").format[Boolean]
  )(Keyword.apply, o => Tuple.fromProductTyped(o))

  // `Case` formatters
  implicit val formatRepaymentClaim: Format[RepaymentClaim] = (
    (JsPath \ "dvrNumber").formatNullable[String] and
      (JsPath \ "dateForRepayment").formatNullable[Instant]
  )(RepaymentClaim.apply, o => Tuple.fromProductTyped(o))
  implicit val formatAddress: Format[Address] = (
    (JsPath \ "buildingAndStreet").format[String] and
      (JsPath \ "townOrCity").format[String] and
      (JsPath \ "county").formatNullable[String] and
      (JsPath \ "postCode").formatNullable[String]
  )(Address.apply, o => Tuple.fromProductTyped(o))
  implicit val formatTraderContactDetails: Format[TraderContactDetails] = (
    (JsPath \ "email").formatNullable[String] and
      (JsPath \ "phone").formatNullable[String] and
      (JsPath \ "address").formatNullable[Address]
  )(TraderContactDetails.apply, o => Tuple.fromProductTyped(o))

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

  implicit val formatEORIDetails: Format[EORIDetails] = (
    (JsPath \ "eori").format[String] and
      (JsPath \ "businessName").format[String] and
      (JsPath \ "addressLine1").format[String] and
      (JsPath \ "addressLine2").format[String] and
      (JsPath \ "addressLine3").format[String] and
      (JsPath \ "postcode").format[String] and
      (JsPath \ "country").format[String]
  )(EORIDetails.apply, o => Tuple.fromProductTyped(o))
  implicit val formatAttachment: Format[Attachment] = (
    (JsPath \ "id").format[String] and
      (JsPath \ "public").format[Boolean] and
      (JsPath \ "operator").formatNullable[Operator] and
      (JsPath \ "timestamp").format[Instant] and
      (JsPath \ "description").formatNullable[String] and
      (JsPath \ "shouldPublishToRulings").format[Boolean]
  )(Attachment.apply, o => Tuple.fromProductTyped(o))

  implicit val formatAgentDetails: Format[AgentDetails] = (
    (JsPath \ "eoriDetails").format[EORIDetails] and
      (JsPath \ "letterOfAuthorisation").formatNullable[Attachment]
  )(AgentDetails.apply, o => Tuple.fromProductTyped(o))
  implicit val formatContact: Format[Contact] = (
    (JsPath \ "name").format[String] and
      (JsPath \ "email").format[String] and
      (JsPath \ "phone").formatNullable[String]
  )(Contact.apply, o => Tuple.fromProductTyped(o))
  implicit val messageLoggedFormat: Format[Message] = (
    (JsPath \ "name").format[String] and
      (JsPath \ "date").format[Instant] and
      (JsPath \ "message").format[String]
  )(Message.apply, o => Tuple.fromProductTyped(o))

  implicit val formatLiabilityOrder: Format[LiabilityOrder] = (
    (JsPath \ "contact").format[Contact] and
      (JsPath \ "goodName").formatNullable[String] and
      (JsPath \ "status").format[LiabilityStatus] and
      (JsPath \ "traderName").format[String] and
      (JsPath \ "entryDate").formatNullable[Instant] and
      (JsPath \ "entryNumber").formatNullable[String] and
      (JsPath \ "traderCommodityCode").formatNullable[String] and
      (JsPath \ "officerCommodityCode").formatNullable[String] and
      (JsPath \ "btiReference").formatNullable[String] and
      (JsPath \ "repaymentClaim").formatNullable[RepaymentClaim] and
      (JsPath \ "dateOfReceipt").formatNullable[Instant] and
      (JsPath \ "traderContactDetails").formatNullable[TraderContactDetails] and
      (JsPath \ "agentName").formatNullable[String] and
      (JsPath \ "port").formatNullable[String]
  )(LiabilityOrder.apply, o => Tuple.fromProductTyped(o))
  implicit val formatLiabilityOrderWrites: OWrites[LiabilityOrder] =
    Json.writes[LiabilityOrder]
  implicit val formatBTIApplication: OFormat[BTIApplication]            = Json.format[BTIApplication]
  implicit val formatCorrespondence: OFormat[CorrespondenceApplication] = Json.format[CorrespondenceApplication]
  implicit val formatMisc: OFormat[MiscApplication]                     = Json.format[MiscApplication]

  implicit val formatApplication: Format[Application] = Union
    .from[Application]("type")
    .and[BTIApplication](ApplicationType.BTI.toString)
    .and[LiabilityOrder](ApplicationType.LIABILITY_ORDER.toString)
    .and[CorrespondenceApplication](ApplicationType.CORRESPONDENCE.toString)
    .and[MiscApplication](ApplicationType.MISCELLANEOUS.toString)
    .format

  implicit val formatAppeal: OFormat[Appeal] = Json.format[Appeal]
  implicit val formatCancellation: Format[Cancellation] = (
    (JsPath \ "reason").format[CancelReason] and
      (JsPath \ "applicationForExtendedUse").format[Boolean]
  )(Cancellation.apply, o => Tuple.fromProductTyped(o))
  implicit val formatDecision: OFormat[Decision] = Json.format[Decision]
  implicit val formatSample: OFormat[Sample]     = Json.format[Sample]

  implicit val formatCase: OFormat[Case]              = Json.using[Json.WithDefaultValues].format[Case]
  implicit val formatNewCase: OFormat[NewCaseRequest] = Json.format[NewCaseRequest]
  implicit val formatCaseHeader: Format[CaseHeader] = (
    (JsPath \ "reference").format[String] and
      (JsPath \ "assignee").formatNullable[Operator] and
      (JsPath \ "team").formatNullable[String] and
      (JsPath \ "goodsName").formatNullable[String] and
      (JsPath \ "caseType").format[ApplicationType.Value] and
      (JsPath \ "status").format[CaseStatus.Value] and
      (JsPath \ "daysElapsed").format[Long] and
      (JsPath \ "liabilityStatus").formatNullable[LiabilityStatus]
  )(CaseHeader.apply, o => Tuple.fromProductTyped(o))
  implicit val formatCaseKeyword: Format[CaseKeyword] = (
    (JsPath \ "keyword").format[Keyword] and
      (JsPath \ "cases").format[List[CaseHeader]]
  )(CaseKeyword.apply, o => Tuple.fromProductTyped(o))

  // `Event` formatters
  implicit val formatCaseStatusChange: Format[CaseStatusChange] = (
    (JsPath \ "from").format[CaseStatus] and
      (JsPath \ "to").format[CaseStatus] and
      (JsPath \ "comment").formatNullable[String] and
      (JsPath \ "attachmentId").formatNullable[String]
  )(CaseStatusChange.apply, o => Tuple.fromProductTyped(o))
  implicit val formatCaseStatusChangeWrites: OWrites[CaseStatusChange] =
    Json.writes[CaseStatusChange]
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

  implicit val formatQueueChange: Format[QueueChange] = (
    (JsPath \ "from").formatNullable[String] and
      (JsPath \ "to").formatNullable[String] and
      (JsPath \ "comment").formatNullable[String]
  )(QueueChange.apply, o => Tuple.fromProductTyped(o))
  implicit val formatQueueChangeWrites: OWrites[QueueChange] =
    Json.writes[QueueChange]

  implicit val formatCaseCreated: OFormat[CaseCreated]                   = Json.format[CaseCreated]
  implicit val formatExpertAdviceReceived: OFormat[ExpertAdviceReceived] = Json.format[ExpertAdviceReceived]

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

  implicit val formatReportField: Format[ReportField[?]] = Union
    .from[ReportField[?]]("type")
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

  implicit val formatReportResultField: Format[ReportResultField[?]] = Union
    .from[ReportResultField[?]]("type")
    .and[NumberResultField](ReportFieldType.Number.name)
    .and[StatusResultField](ReportFieldType.Status.name)
    .and[LiabilityStatusResultField](ReportFieldType.LiabilityStatus.name)
    .and[CaseTypeResultField](ReportFieldType.CaseType.name)
    .and[DateResultField](ReportFieldType.Date.name)
    .and[StringResultField](ReportFieldType.String.name)
    .format

  implicit val formatSimpleResultGroup: OFormat[SimpleResultGroup] = Json.format[SimpleResultGroup]

  implicit val numberResultFieldListWrites: Writes[List[NumberResultField]] =
    Writes.list[NumberResultField].contramap(identity)

  implicit val formatCaseResultGroup: Format[CaseResultGroup] = (
    (JsPath \ "count").format[Long] and
      (JsPath \ "groupKey").format[NonEmptySeq[ReportResultField[?]]] and
      (JsPath \ "maxFields").format[List[NumberResultField]] and
      (JsPath \ "cases").format[List[Case]]
  )(CaseResultGroup.apply, o => Tuple.fromProductTyped(o))

  implicit val readResultGroup: Reads[ResultGroup] =
    (__ \ "cases").readNullable[List[Case]].flatMap {
      case Some(_) => formatCaseResultGroup.widen[ResultGroup]
      case None    => formatSimpleResultGroup.widen[ResultGroup]
    }

  implicit val writeResultGroup: OWrites[ResultGroup] = OWrites[ResultGroup] {
    case caseResult: CaseResultGroup     => Json.writes[CaseResultGroup].writes(caseResult)
    case simpleResult: SimpleResultGroup => Json.writes[SimpleResultGroup].writes(simpleResult)
  }

  implicit val formatResultGroup: OFormat[ResultGroup] = OFormat(readResultGroup, writeResultGroup)

  implicit val formatQueueResultGroup: Format[QueueResultGroup] = (
    (JsPath \ "count").format[Int] and
      (JsPath \ "team").formatNullable[String] and
      (JsPath \ "caseType").format[ApplicationType.Value]
  )(QueueResultGroup.apply, o => Tuple.fromProductTyped(o))
}
