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

package uk.gov.hmrc.bindingtariffclassification.model.reporting

import cats.data.NonEmptySeq
import java.time.Instant
import uk.gov.hmrc.bindingtariffclassification.model._

sealed abstract class ReportField[A](val fieldName: String, val underlyingField: String)
    extends Product
    with Serializable {
  def withValue(value: Option[A]): ReportResultField[A]
}

case class NumberField(override val fieldName: String, override val underlyingField: String)
    extends ReportField[Long](fieldName, underlyingField) {
  def withValue(value: Option[Long]): NumberResultField = NumberResultField(fieldName, value)
}
case class StatusField(override val fieldName: String, override val underlyingField: String)
    extends ReportField[PseudoCaseStatus.Value](fieldName, underlyingField) {
  def withValue(value: Option[PseudoCaseStatus.Value]): StatusResultField = StatusResultField(fieldName, value)
}
case class CaseTypeField(override val fieldName: String, override val underlyingField: String)
    extends ReportField[ApplicationType.Value](fieldName, underlyingField) {
  def withValue(value: Option[ApplicationType.Value]): CaseTypeResultField = CaseTypeResultField(fieldName, value)
}
case class ChapterField(override val fieldName: String, override val underlyingField: String)
    extends ReportField[String](fieldName, underlyingField) {
  def withValue(value: Option[String]): StringResultField = StringResultField(fieldName, value)
}
case class DateField(override val fieldName: String, override val underlyingField: String)
    extends ReportField[Instant](fieldName, underlyingField) {
  def withValue(value: Option[Instant]): DateResultField = DateResultField(fieldName, value)
}
case class StringField(override val fieldName: String, override val underlyingField: String)
    extends ReportField[String](fieldName, underlyingField) {
  def withValue(value: Option[String]): StringResultField = StringResultField(fieldName, value)
}
case class DaysSinceField(override val fieldName: String, override val underlyingField: String)
    extends ReportField[Long](fieldName, underlyingField) {
  def withValue(value: Option[Long]): NumberResultField = NumberResultField(fieldName, value)
}
case class CoalesceField(override val fieldName: String, fieldChoices: NonEmptySeq[String])
    extends ReportField[String](fieldName, fieldChoices.head) {
  def withValue(value: Option[String]): StringResultField = StringResultField(fieldName, value)
}
case class LiabilityStatusField(override val fieldName: String, override val underlyingField: String)
    extends ReportField[LiabilityStatus.Value](fieldName, underlyingField) {
  def withValue(value: Option[LiabilityStatus.Value]): LiabilityStatusResultField =
    LiabilityStatusResultField(fieldName, value)
}

object ReportField {
  val Count: NumberField     = NumberField("count", "count")
  val Reference: StringField = StringField("reference", "reference")
  private val Description    = StringField("description", "application.detailedDescription")
  val CaseSource: CoalesceField =
    CoalesceField("source", NonEmptySeq.of("application.correspondenceStarter", "application.caseType"))
  val Status: StatusField            = StatusField("status", "status")
  val CaseType: CaseTypeField        = CaseTypeField("case_type", "application.type")
  val Chapter: ChapterField          = ChapterField("chapter", "decision.bindingCommodityCode")
  private val GoodsName: StringField = StringField("goods_name", "application.goodName")
  private val TraderName: CoalesceField =
    CoalesceField("trader_name", NonEmptySeq.of("application.traderName", "application.holder.businessName"))
  private val BusinessName: StringField     = StringField("business_name", "application.holder.businessName")
  val User: StringField                     = StringField("assigned_user", "assignee.id")
  val Team: StringField                     = StringField("assigned_team", "queueId")
  val DateCreated: DateField                = DateField("date_created", "createdDate")
  private val DateCompleted                 = DateField("date_completed", "decision.effectiveStartDate")
  val DateExpired: DateField                = DateField("date_expired", "decision.effectiveEndDate")
  val ElapsedDays: NumberField              = NumberField("elapsed_days", "daysElapsed")
  val TotalDays: DaysSinceField             = DaysSinceField("total_days", "createdDate")
  val ReferredDays: NumberField             = NumberField("referred_days", "referredDaysElapsed")
  val LiabilityStatus: LiabilityStatusField = LiabilityStatusField("liability_status", "application.status")
  val ContactName: StringField              = StringField("contact_name", "application.contact.name")
  val ContactEmail: StringField             = StringField("contact_email", "application.contact.email")

  val fields: Map[String, ReportField[_]] = List(
    Count,
    Reference,
    Description,
    CaseSource,
    Status,
    CaseType,
    Chapter,
    GoodsName,
    TraderName,
    BusinessName,
    User,
    Team,
    DateCreated,
    DateCompleted,
    DateExpired,
    ElapsedDays,
    TotalDays,
    ReferredDays,
    LiabilityStatus,
    ContactName,
    ContactEmail
  ).map(field => field.fieldName -> field).toMap

  val encryptedFields: Seq[ReportField[_]] = Seq(ContactName, ContactEmail)
}
