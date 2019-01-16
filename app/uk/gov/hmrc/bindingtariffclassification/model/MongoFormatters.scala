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

import java.time.{Instant, ZoneId, ZonedDateTime}

import play.api.libs.json._
import uk.gov.hmrc.play.json.Union

object MongoFormatters {

  implicit val instantFormat: OFormat[ZonedDateTime] = new OFormat[ZonedDateTime] {
    override def writes(datetime: ZonedDateTime): JsObject = {
      Json.obj("$date" -> datetime.toInstant.toEpochMilli)
    }

    override def reads(json: JsValue): JsResult[ZonedDateTime] = {
      json match {
        case JsObject(map) if map.contains("$date") =>
          map("$date") match {
            case JsNumber(v) => JsSuccess(Instant.ofEpochMilli(v.toLong).atZone(ZoneId.systemDefault()))
            case _ => JsError("Unexpected Instant Format")
          }
        case _ => JsError("Unexpected Instant Format")
      }
    }
  }


  implicit val formatSequence = Json.format[Sequence]
  // `Case` formatters
  implicit val formatCaseStatus = EnumJson.format(CaseStatus)
  implicit val formatApplicationType = EnumJson.format(ApplicationType)
  implicit val formatLiabilityStatus = EnumJson.format(LiabilityStatus)

  implicit val formatEORIDetails = Json.format[EORIDetails]
  implicit val formatAttachment = Json.format[Attachment]
  implicit val formatAgentDetails = Json.format[AgentDetails]
  implicit val formatContact = Json.format[Contact]

  implicit val formatLiabilityOrder = Json.format[LiabilityOrder]
  implicit val formatBTIApplication = Json.format[BTIApplication]
  implicit val formatApplication = Union.from[Application]("type")
    .and[BTIApplication](ApplicationType.BTI.toString)
    .and[LiabilityOrder](ApplicationType.LIABILITY_ORDER.toString)
    .format

  implicit val formatAppeal = Json.format[Appeal]
  implicit val formatDecision = Json.format[Decision]

  implicit val formatCase = Json.format[Case]

  implicit val formatStatus = Json.format[Status]

  // `Event` formatters
  implicit val formatCaseStatusChange = Json.format[CaseStatusChange]
  implicit val formatNote = Json.format[Note]

  implicit val formatEventDetail = Union.from[Details]("type")
    .and[CaseStatusChange](EventType.CASE_STATUS_CHANGE.toString)
    .and[Note](EventType.NOTE.toString)
    .format

  implicit val formatOperator = Json.format[Operator]
  implicit val formatEvent = Json.format[Event]
  implicit val formatSchedulerRunEvent = Json.format[SchedulerRunEvent]
}
