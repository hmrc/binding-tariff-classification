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

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.*
import uk.gov.hmrc.bindingtariffclassification.model.AppealStatus.AppealStatus
import uk.gov.hmrc.bindingtariffclassification.model.AppealType.AppealType
import uk.gov.hmrc.bindingtariffclassification.model.CancelReason.CancelReason
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.LiabilityStatus.LiabilityStatus
import uk.gov.hmrc.bindingtariffclassification.model.MiscCaseType.MiscCaseType
import uk.gov.hmrc.bindingtariffclassification.model.ReferralReason.ReferralReason
import uk.gov.hmrc.bindingtariffclassification.model.SampleReturn.SampleReturn
import uk.gov.hmrc.bindingtariffclassification.model.SampleSend.SampleSend
import uk.gov.hmrc.bindingtariffclassification.model.SampleStatus.SampleStatus
import uk.gov.hmrc.bindingtariffclassification.model.RejectReason.RejectReason
import uk.gov.hmrc.bindingtariffclassification.model.Role.Role
import uk.gov.hmrc.bindingtariffclassification.utils.{JsonUtil, Union}

import java.time.{Instant, LocalDateTime, ZoneOffset, ZonedDateTime}
import scala.util.{Success, Try}

object MongoFormatters {
  implicit val formatInstant: OFormat[Instant] = new OFormat[Instant] {
    override def writes(datetime: Instant): JsObject =
      Json.obj("$date" -> datetime.toEpochMilli)

    override def reads(json: JsValue): JsResult[Instant] =
      json match {
        case JsObject(map) if map.contains("$date") =>
          map("$date") match {
            case JsNumber(v) => JsSuccess(Instant.ofEpochMilli(v.toLong))
            case JsObject(stringObject) =>
              if (stringObject.contains("$numberLong")) {
                JsSuccess(Instant.ofEpochMilli(BigDecimal(stringObject("$numberLong").as[JsString].value).toLong))
              } else {
                JsError("Unexpected Instant Format")
              }
            case JsString(dateValue) =>
              val parseDateTime = if (dateValue.contains("Z")) { (dateAsString: String) =>
                ZonedDateTime.parse(dateAsString)
              } else { (dateAsString: String) => LocalDateTime.parse(dateAsString) }
              ZonedDateTime.parse(dateValue)
              Try(parseDateTime(dateValue)) match {
                case Success(value: LocalDateTime) => JsSuccess(value.toInstant(ZoneOffset.UTC))
                case Success(value: ZonedDateTime) => JsSuccess(value.toInstant)
                case _                             => JsError("Unexpected Instant Format")
              }
            case _ => JsError("Unexpected Instant Format")
          }
        case _ => JsError("Unexpected Instant Format")
      }
  }

  implicit val formatZonedDateTime: OFormat[ZonedDateTime] =
    formatInstant.bimap(
      instant => ZonedDateTime.ofInstant(instant, ZoneOffset.UTC),
      datetime => datetime.toInstant
    )

  // User formatters
  implicit val role: Format[Role.Value]                             = EnumJson.format(Role)
  implicit val formatApplicationType: Format[ApplicationType.Value] = EnumJson.format(ApplicationType)

  implicit val formatOperator: Format[Operator] = (
    (JsPath \ "id").format[String] and
      (JsPath \ "name").formatNullable[String] and
      (JsPath \ "email").formatNullable[String] and
      (JsPath \ "role").format[Role] and
      (JsPath \ "memberOfTeams").format[List[String]] and
      (JsPath \ "managerOfTeams").format[List[String]] and
      (JsPath \ "deleted").format[Boolean]
  )(Operator.apply, o => Tuple.fromProductTyped(o))

  implicit val formatKeywords: Format[Keyword] = (
    (JsPath \ "name").format[String] and
      (JsPath \ "approved").format[Boolean]
  )(Keyword.apply, o => Tuple.fromProductTyped(o))

  implicit val formatSequence: Format[Sequence] = (
    (JsPath \ "name").format[String] and
      (JsPath \ "value").format[Long]
  )(Sequence.apply, o => Tuple.fromProductTyped(o))

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
  implicit val formatAppealStatus: Format[AppealStatus.Value]         = EnumJson.format(AppealStatus)
  implicit val formatAppealType: Format[AppealType.Value]             = EnumJson.format(AppealType)
  implicit val formatSampleStatus: Format[SampleStatus.Value]         = EnumJson.format(SampleStatus)
  implicit val formatSampleReturn: Format[SampleReturn.Value]         = EnumJson.format(SampleReturn)
  implicit val formatSampleSend: Format[SampleSend.Value]             = EnumJson.format(SampleSend)
  implicit val formatCancelReason: Format[CancelReason.Value]         = EnumJson.format(CancelReason)
  implicit val formatReferralReason: Format[ReferralReason.Value]     = EnumJson.format(ReferralReason)
  implicit val formatRejectedReason: Format[RejectReason.Value]       = EnumJson.format(RejectReason)
  implicit val formatLiabilityStatus: Format[LiabilityStatus.Value]   = EnumJson.format(LiabilityStatus)
  implicit val miscCaseType: Format[MiscCaseType.Value]               = EnumJson.format(MiscCaseType)
  implicit val formatAttachment: Format[Attachment] = (
    (JsPath \ "id").format[String] and
      (JsPath \ "public").format[Boolean] and
      (JsPath \ "operator").formatNullable[Operator] and
      (JsPath \ "timestamp").format[Instant] and
      (JsPath \ "description").formatNullable[String] and
      (JsPath \ "shouldPublishToRulings").format[Boolean]
  )(Attachment.apply, o => Tuple.fromProductTyped(o))

  implicit val formatEORIDetails: Format[EORIDetails] = (
    (JsPath \ "eori").format[String] and
      (JsPath \ "businessName").format[String] and
      (JsPath \ "addressLine1").format[String] and
      (JsPath \ "addressLine2").format[String] and
      (JsPath \ "addressLine3").format[String] and
      (JsPath \ "postcode").format[String] and
      (JsPath \ "country").format[String]
  )(EORIDetails.apply, o => Tuple.fromProductTyped(o))

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
  implicit val formatBTIApplication: Format[BTIApplication] = (
    (JsPath \ "holder").format[EORIDetails] and
      (JsPath \ "contact").format[Contact] and
      (JsPath \ "agent").formatNullable[AgentDetails] and
      (JsPath \ "offline").format[Boolean] and
      (JsPath \ "goodName").format[String] and
      (JsPath \ "goodDescription").format[String] and
      (JsPath \ "confidentialInformation").formatNullable[String] and
      (JsPath \ "otherInformation").formatNullable[String] and
      (JsPath \ "reissuedBTIReference").formatNullable[String] and
      (JsPath \ "relatedBTIReference").formatNullable[String] and
      (JsPath \ "relatedBTIReferences").format[List[String]] and
      (JsPath \ "knownLegalProceedings").formatNullable[String] and
      (JsPath \ "envisagedCommodityCode").formatNullable[String] and
      (JsPath \ "sampleToBeProvided").format[Boolean] and
      (JsPath \ "sampleIsHazardous").formatNullable[Boolean] and
      (JsPath \ "sampleToBeReturned").format[Boolean] and
      (JsPath \ "applicationPdf").formatNullable[Attachment]
  )(BTIApplication.apply, o => Tuple.fromProductTyped(o))
  implicit val formatBTIApplicationWrites: OWrites[BTIApplication] =
    Json.writes[BTIApplication]
  implicit val formatCorrespondence: Format[CorrespondenceApplication] = (
    (JsPath \ "correspondenceStarter").formatNullable[String] and
      (JsPath \ "agentName").formatNullable[String] and
      (JsPath \ "address").format[Address] and
      (JsPath \ "contact").format[Contact] and
      (JsPath \ "fax").formatNullable[String] and
      (JsPath \ "summary").format[String] and
      (JsPath \ "detailedDescription").format[String] and
      (JsPath \ "relatedBTIReference").formatNullable[String] and
      (JsPath \ "relatedBTIReferences").format[List[String]] and
      (JsPath \ "sampleToBeProvided").format[Boolean] and
      (JsPath \ "sampleToBeReturned").format[Boolean] and
      (JsPath \ "messagesLogged").format[List[Message]]
  )(CorrespondenceApplication.apply, o => Tuple.fromProductTyped(o))
  implicit val formatCorrespondenceApplicationWrites: OWrites[CorrespondenceApplication] =
    Json.writes[CorrespondenceApplication]
  implicit val formatMisc: Format[MiscApplication] = (
    (JsPath \ "contact").format[Contact] and
      (JsPath \ "name").format[String] and
      (JsPath \ "contactName").formatNullable[String] and
      (JsPath \ "caseType").format[MiscCaseType] and
      (JsPath \ "detailedDescription").formatNullable[String] and
      (JsPath \ "sampleToBeProvided").format[Boolean] and
      (JsPath \ "sampleToBeReturned").format[Boolean] and
      (JsPath \ "messagesLogged").format[List[Message]]
  )(MiscApplication.apply, o => Tuple.fromProductTyped(o))
  implicit val formatMiscWrites: OWrites[MiscApplication] =
    Json.writes[MiscApplication]

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
  implicit val formatSample: Format[Sample] = (
    (JsPath \ "status").formatNullable[SampleStatus] and
      (JsPath \ "requestedBy").formatNullable[Operator] and
      (JsPath \ "returnStatus").formatNullable[SampleReturn] and
      (JsPath \ "whoIsSending").formatNullable[SampleSend]
  )(Sample.apply, o => Tuple.fromProductTyped(o))
  implicit val formatCase: OFormat[Case] = JsonUtil.convertToOFormat(Json.using[Json.WithDefaultValues].format[Case])
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
  implicit val formatManageKeywordsData: Format[ManageKeywordsData] = (
    (JsPath \ "pagedCaseKeywords").format[Paged[CaseKeyword]] and
      (JsPath \ "pagedKeywords").format[Paged[Keyword]]
    )(ManageKeywordsData.apply, o => Tuple.fromProductTyped(o))
  implicit val formatEventType: Format[EventType.Value] = EnumJson.format(EventType)

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
  implicit val formatReferralCaseStatusChange: Format[ReferralCaseStatusChange] = (
    (JsPath \ "from").format[CaseStatus] and
      (JsPath \ "comment").formatNullable[String] and
      (JsPath \ "attachmentId").formatNullable[String] and
      (JsPath \ "referredTo").format[String] and
      (JsPath \ "reason").format[Seq[ReferralReason]]
  )(ReferralCaseStatusChange.apply, o => Tuple.fromProductTyped(o))
  implicit val formatReferralCaseStatusChangeWrites: OWrites[ReferralCaseStatusChange] =
    Json.writes[ReferralCaseStatusChange]

  implicit val formatRejectCaseStatusChange: Format[RejectCaseStatusChange] = (
    (JsPath \ "from").format[CaseStatus] and
      (JsPath \ "to").format[CaseStatus] and
      (JsPath \ "comment").formatNullable[String] and
      (JsPath \ "attachmentId").formatNullable[String] and
      (JsPath \ "reason").format[RejectReason]
  )(RejectCaseStatusChange.apply, o => Tuple.fromProductTyped(o))
  implicit val formatRejectCaseStatusChangeWrites: OWrites[RejectCaseStatusChange] =
    Json.writes[RejectCaseStatusChange]

  implicit val formatCompletedCaseStatusChange: OFormat[CompletedCaseStatusChange] =
    Json.format[CompletedCaseStatusChange]

  implicit val formatAppealStatusChange: Format[AppealStatusChange] = (
    (JsPath \ "appealType").format[AppealType] and
      (JsPath \ "from").format[AppealStatus] and
      (JsPath \ "to").format[AppealStatus] and
      (JsPath \ "comment").formatNullable[String]
  )(AppealStatusChange.apply, o => Tuple.fromProductTyped(o))
  implicit val formatAppealStatusChangeWrites: OWrites[AppealStatusChange] =
    Json.writes[AppealStatusChange]
  implicit val formatAppealAdded: Format[AppealAdded] = (
    (JsPath \ "appealType").format[AppealType] and
      (JsPath \ "appealStatus").format[AppealStatus] and
      (JsPath \ "comment").formatNullable[String]
  )(AppealAdded.apply, o => Tuple.fromProductTyped(o))
  implicit val formatAppealAddedWrites: OWrites[AppealAdded] =
    Json.writes[AppealAdded]

  implicit val formatSampleStatusChange: Format[SampleStatusChange] = (
    (JsPath \ "from").formatNullable[SampleStatus] and
      (JsPath \ "to").formatNullable[SampleStatus] and
      (JsPath \ "comment").formatNullable[String]
  )(SampleStatusChange.apply, o => Tuple.fromProductTyped(o))
  implicit val formatSampleStatusChangeWrites: OWrites[SampleStatusChange] =
    Json.writes[SampleStatusChange]

  implicit val formatSampleReturnChange: Format[SampleReturnChange] = (
    (JsPath \ "from").formatNullable[SampleReturn] and
      (JsPath \ "to").formatNullable[SampleReturn] and
      (JsPath \ "comment").formatNullable[String]
  )(SampleReturnChange.apply, o => Tuple.fromProductTyped(o))
  implicit val formatSampleReturnChangeWrites: OWrites[SampleReturnChange] =
    Json.writes[SampleReturnChange]

  implicit val formatSampleSendChange: Format[SampleSendChange] = (
    (JsPath \ "from").formatNullable[SampleSend] and
      (JsPath \ "to").formatNullable[SampleSend] and
      (JsPath \ "comment").formatNullable[String]
  )(SampleSendChange.apply, o => Tuple.fromProductTyped(o))
  implicit val formatSampleSendChangeWrites: OWrites[SampleSendChange] =
    Json.writes[SampleSendChange]

  implicit val formatExtendedUseStatusChange: Format[ExtendedUseStatusChange] = (
    (JsPath \ "from").format[Boolean] and
      (JsPath \ "to").format[Boolean] and
      (JsPath \ "comment").formatNullable[String]
  )(ExtendedUseStatusChange.apply, o => Tuple.fromProductTyped(o))
  implicit val formatExtendedUseStatusChangeWrites: OWrites[ExtendedUseStatusChange] =
    Json.writes[ExtendedUseStatusChange]

  implicit val formatAssignmentChange: Format[AssignmentChange] = (
    (JsPath \ "from").formatNullable[Operator] and
      (JsPath \ "to").formatNullable[Operator] and
      (JsPath \ "comment").formatNullable[String]
  )(AssignmentChange.apply, o => Tuple.fromProductTyped(o))
  implicit val formatAssignmentChangeWrites: OWrites[AssignmentChange] =
    Json.writes[AssignmentChange]

  implicit val formatQueueChange: Format[QueueChange] = (
    (JsPath \ "from").formatNullable[String] and
      (JsPath \ "to").formatNullable[String] and
      (JsPath \ "comment").formatNullable[String]
  )(QueueChange.apply, o => Tuple.fromProductTyped(o))
  implicit val formatQueueChangeWrites: OWrites[QueueChange] =
    Json.writes[QueueChange]

  implicit val formatNote: OFormat[Note]                                 = Json.format[Note]
  implicit val formatCaseCreated: OFormat[CaseCreated]                   = Json.format[CaseCreated]
  implicit val formatExpertAdviceReceived: OFormat[ExpertAdviceReceived] = Json.format[ExpertAdviceReceived]

  implicit val formatEventDetail: Format[Details] = Union
    .from[Details]("type")
    .and[CaseStatusChange](EventType.CASE_STATUS_CHANGE.toString)
    .and[CancellationCaseStatusChange](EventType.CASE_CANCELLATION.toString)
    .and[ReferralCaseStatusChange](EventType.CASE_REFERRAL.toString)
    .and[RejectCaseStatusChange](EventType.CASE_REJECTED.toString)
    .and[CompletedCaseStatusChange](EventType.CASE_COMPLETED.toString)
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

  implicit val formatEvent: OFormat[Event]             = Json.format[Event]
  implicit val formatJobRunEvent: OFormat[JobRunEvent] = Json.format[JobRunEvent]
}
