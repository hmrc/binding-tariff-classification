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

package uk.gov.hmrc.bindingtariffclassification.model

import play.api.libs.json._
import play.json.extra.{InvariantFormat, Jsonx}
import uk.gov.hmrc.bindingtariffclassification.model.reporting.ReportResult
import uk.gov.hmrc.play.json.Union

object RESTFormatters {


  // `Case` formatters
  implicit val formatCaseStatus: Format[CaseStatus.Value] = EnumJson.format(CaseStatus)
  implicit val formatApplicationType: Format[ApplicationType.Value] = EnumJson.format(ApplicationType)
  implicit val formatLiabilityStatus: Format[LiabilityStatus.Value] = EnumJson.format(LiabilityStatus)
  implicit val formatAppealStatus: Format[AppealStatus.Value] = EnumJson.format(AppealStatus)
  implicit val formatAppealType: Format[AppealType.Value] = EnumJson.format(AppealType)
  implicit val formatCancelReason: Format[CancelReason.Value] = EnumJson.format(CancelReason)

  implicit val formatReportResult: OFormat[ReportResult] = Json.format[ReportResult]
  implicit val formatImportExport: Format[ImportExport.Value] = EnumJson.format(ImportExport)

  implicit val formatOperator: OFormat[Operator] = Json.format[Operator]
  implicit val formatEORIDetails: OFormat[EORIDetails] = Json.format[EORIDetails]
  implicit val formatAttachment: OFormat[Attachment] = Json.format[Attachment]
  implicit val formatAgentDetails: OFormat[AgentDetails] = Json.format[AgentDetails]
  implicit val formatContact: OFormat[Contact] = Json.format[Contact]

  implicit val formatLiabilityOrder: OFormat[LiabilityOrder] = Json.format[LiabilityOrder]
  implicit val formatBTIApplication: OFormat[BTIApplication] = Json.format[BTIApplication]
  implicit val formatApplication: Format[Application] = Union.from[Application]("type")
    .and[BTIApplication](ApplicationType.BTI.toString)
    .and[LiabilityOrder](ApplicationType.LIABILITY_ORDER.toString)
    .format

  implicit val formatAppeal: OFormat[Appeal] = Json.format[Appeal]
  implicit val formatCancellation: OFormat[Cancellation] = Json.format[Cancellation]
  implicit val formatDecision: OFormat[Decision] = Json.format[Decision]

  implicit val formatCase: InvariantFormat[Case] = Jsonx.formatCaseClass[Case]
  implicit val formatNewCase: OFormat[NewCaseRequest] = Json.format[NewCaseRequest]

  // `Event` formatters
  implicit val formatCaseStatusChange: OFormat[CaseStatusChange] = Json.format[CaseStatusChange]
  implicit val formatAppealStatusChange: OFormat[AppealStatusChange] = Json.format[AppealStatusChange]
  implicit val formatAppealAdded: OFormat[AppealAdded] = Json.format[AppealAdded]
  implicit val formatExtendedUseStatusChange: OFormat[ExtendedUseStatusChange] = Json.format[ExtendedUseStatusChange]
  implicit val formatAssignmentChange: OFormat[AssignmentChange] = Json.format[AssignmentChange]
  implicit val formatQueueChange: OFormat[QueueChange] = Json.format[QueueChange]

  implicit val formatNote: OFormat[Note] = Json.format[Note]

  implicit val formatEventDetail: Format[Details] = Union.from[Details]("type")
    .and[CaseStatusChange](EventType.CASE_STATUS_CHANGE.toString)
    .and[AppealStatusChange](EventType.APPEAL_STATUS_CHANGE.toString)
    .and[AppealAdded](EventType.APPEAL_ADDED.toString)
    .and[ExtendedUseStatusChange](EventType.EXTENDED_USE_STATUS_CHANGE.toString)
    .and[AssignmentChange](EventType.ASSIGNMENT_CHANGE.toString)
    .and[QueueChange](EventType.QUEUE_CHANGE.toString)
    .and[Note](EventType.NOTE.toString)
    .format


  implicit val formatEvent: OFormat[Event] = Json.format[Event]
  implicit val formatNewEventRequest: OFormat[NewEventRequest] = Json.format[NewEventRequest]

  implicit val formatBankHoliday: OFormat[BankHoliday] = Json.format[BankHoliday]
  implicit val formatBankHolidaysSet: OFormat[BankHolidaySet] = Json.format[BankHolidaySet]
  implicit val formatBankHolidaysResponse: OFormat[BankHolidaysResponse] = Json.format[BankHolidaysResponse]
}
