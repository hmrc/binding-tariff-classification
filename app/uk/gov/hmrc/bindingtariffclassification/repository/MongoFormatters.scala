/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.repository

import play.api.libs.json._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.play.json.Union

trait MongoFormatters {

  implicit val formatCaseStatus = EnumJson.enumFormat(CaseStatus)
  implicit val formatApplicationType = EnumJson.enumFormat(ApplicationType)
  implicit val formatLiabilityOrderType = EnumJson.enumFormat(LiabilityOrderType)

  implicit val formatEORIDetails = Json.format[EORIDetails]
  implicit val formatContact = Json.format[Contact]
  implicit val formatLiabilityOrder = Json.format[LiabilityOrder]
  implicit val formatBTIApplication = Json.format[BTIApplication]
  implicit val formatBTIOfflineApplication = Json.format[BTIOfflineApplication]

  implicit val formatAppeal = Json.format[Appeal]

  implicit val formatApplication = Union.from[Application]("applicationType")
    .and[BTIApplication](ApplicationType.BTI.toString)
    .and[BTIOfflineApplication](ApplicationType.OFFLINE_BTI.toString)
    .and[LiabilityOrder](ApplicationType.LIABILITY_ORDER.toString)
    .format

  implicit val formatDecision = Json.format[Decision]
  implicit val formatCase = Json.format[Case]


  implicit val formatAttachment = Json.format[Attachment]
  implicit val formatCaseStatusChange = Json.format[CaseStatusChange]
  implicit val formatNote = Json.format[Note]

  implicit val formatEventDetail = Union.from[Details]("type")
    .and[Attachment](EventType.ATTACHMENT.toString)
    .and[CaseStatusChange](EventType.CASE_STATUS_CHANGE.toString)
    .and[Note](EventType.NOTE.toString)
    .format

  implicit val formatEvent = Json.format[Event]
}

object MongoFormatters extends MongoFormatters

object EnumJson {

  def enumReads[E <: Enumeration](enum: E): Reads[E#Value] = new Reads[E#Value] {
    def reads(json: JsValue): JsResult[E#Value] = json match {
      case JsString(s) =>
        try {
          JsSuccess(enum.withName(s))
        } catch {
          case _: NoSuchElementException =>
            throw new InvalidEnumException(enum.getClass.getSimpleName, s)
        }
      case _ => JsError("String value expected")
    }
  }

  implicit def enumWrites[E <: Enumeration]: Writes[E#Value] = new Writes[E#Value] {
    def writes(v: E#Value): JsValue = JsString(v.toString)
  }

  implicit def enumFormat[E <: Enumeration](enum: E): Format[E#Value] = {
    Format(enumReads(enum), enumWrites)
  }

}

class InvalidEnumException(className: String, input: String) extends RuntimeException(s"Enumeration expected of type: '$className', but it does not contain '$input'")
