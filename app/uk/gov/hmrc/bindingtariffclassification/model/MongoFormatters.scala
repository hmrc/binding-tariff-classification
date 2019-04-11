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

import java.time.Instant

import play.api.libs.json._
import play.json.extra.Jsonx
import uk.gov.hmrc.bindingtariffclassification.model.reporting.ReportResult
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
  implicit val formatSequence = Json.format[Sequence]

  // `Case` formatters
  implicit val formatReportResult = Json.format[ReportResult]
  implicit val formatOperator = Json.format[Operator]
  implicit val formatCaseStatus = EnumJson.format(CaseStatus)
  implicit val formatPseudoCaseStatus = EnumJson.format(PseudoCaseStatus)
  implicit val formatAppealStatus = EnumJson.format(AppealStatus)
  implicit val formatReviewStatus = EnumJson.format(ReviewStatus)
  implicit val formatCancelReason = EnumJson.format(CancelReason)
  implicit val formatApplicationType = EnumJson.format(ApplicationType)
  implicit val formatLiabilityStatus = EnumJson.format(LiabilityStatus)
  implicit val formatAttachment = Json.format[Attachment]
  implicit val formatEORIDetails = Json.format[EORIDetails]
  implicit val formatAgentDetails = Json.format[AgentDetails]
  implicit val formatContact = Json.format[Contact]

  implicit val formatImportExport = EnumJson.format(ImportExport)

  implicit val formatLiabilityOrder = Json.format[LiabilityOrder]
  implicit val formatBTIApplication = Json.format[BTIApplication]
  implicit val formatApplication = Union.from[Application]("type")
    .and[BTIApplication](ApplicationType.BTI.toString)
    .and[LiabilityOrder](ApplicationType.LIABILITY_ORDER.toString)
    .format

  implicit val formatAppeal = Json.format[Appeal]
  implicit val formatReview = Json.format[Review]
  implicit val formatCancellation = Json.format[Cancellation]
  implicit val formatDecision = Json.format[Decision]
  implicit val formatCase = JsonUtil.convertToOFormat(Jsonx.formatCaseClass[Case])

  // `Event` formatters
  implicit val formatCaseStatusChange = Json.format[CaseStatusChange]
  implicit val formatAppealStatusChange = Json.format[AppealStatusChange]
  implicit val formatReviewStatusChange = Json.format[ReviewStatusChange]
  implicit val formatExtendedUseStatusChange = Json.format[ExtendedUseStatusChange]
  implicit val formatAssignmentChange = Json.format[AssignmentChange]
  implicit val formatQueueChange = Json.format[QueueChange]
  implicit val formatNote = Json.format[Note]

  implicit val formatEventDetail = Union.from[Details]("type")
    .and[CaseStatusChange](EventType.CASE_STATUS_CHANGE.toString)
    .and[AppealStatusChange](EventType.APPEAL_STATUS_CHANGE.toString)
    .and[ReviewStatusChange](EventType.REVIEW_STATUS_CHANGE.toString)
    .and[ExtendedUseStatusChange](EventType.EXTENDED_USE_STATUS_CHANGE.toString)
    .and[AssignmentChange](EventType.ASSIGNMENT_CHANGE.toString)
    .and[QueueChange](EventType.QUEUE_CHANGE.toString)
    .and[Note](EventType.NOTE.toString)
    .format


  implicit val formatEventType = EnumJson.format(EventType)
  implicit val formatEvent = Json.format[Event]
  implicit val formatSchedulerRunEvent = Json.format[SchedulerRunEvent]

}
