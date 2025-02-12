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
import cats.syntax.all._
import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.bindingtariffclassification.model.{ApplicationType, LiabilityStatus, PseudoCaseStatus}
import uk.gov.hmrc.bindingtariffclassification.sort.SortDirection

sealed abstract class Report extends Product with Serializable {
  def sortBy: ReportField[_]
  def sortOrder: SortDirection.Value
  def caseTypes: Set[ApplicationType.Value]
  def statuses: Set[PseudoCaseStatus.Value]
  def liabilityStatuses: Set[LiabilityStatus.Value]
  def teams: Set[String]
  def dateRange: InstantRange

  def dueToExpireRange: Boolean
}

case class SummaryReport(
  groupBy: NonEmptySeq[ReportField[_]],
  sortBy: ReportField[_],
  sortOrder: SortDirection.Value = SortDirection.ASCENDING,
  caseTypes: Set[ApplicationType.Value] = Set.empty,
  statuses: Set[PseudoCaseStatus.Value] = Set.empty,
  liabilityStatuses: Set[LiabilityStatus.Value] = Set.empty,
  teams: Set[String] = Set.empty,
  dateRange: InstantRange = InstantRange.allTime,
  maxFields: Seq[ReportField[Long]] = Seq.empty,
  includeCases: Boolean = false,
  dueToExpireRange: Boolean = false
) extends Report

object SummaryReport {
  private val dateRangeKey         = "date"
  private val groupByKey           = "group_by"
  private val sortByKey            = "sort_by"
  private val sortOrderKey         = "sort_order"
  private val caseTypesKey         = "case_type"
  private val teamsKey             = "team"
  private val maxFieldsKey         = "max_fields"
  private val includeCasesKey      = "include_cases"
  private val statusesKey          = "status"
  private val liabilityStatusesKey = "liability_status"

  implicit def summaryReportQueryStringBindable(implicit
    stringBindable: QueryStringBindable[String],
    boolBindable: QueryStringBindable[Boolean],
    rangeBindable: QueryStringBindable[InstantRange]
  ): QueryStringBindable[SummaryReport] = new QueryStringBindable[SummaryReport] {
    import uk.gov.hmrc.bindingtariffclassification.model.utils.BinderUtil._

    override def bind(key: String, requestParams: Map[String, Seq[String]]): Option[Either[String, SummaryReport]] = {
      val includeCases = boolBindable.bind(includeCasesKey, requestParams).getOrElse(Right(false))
      val dateRange    = rangeBindable.bind(dateRangeKey, requestParams).getOrElse(Right(InstantRange.allTime))
      val sortBy       = param(sortByKey)(requestParams).flatMap(ReportField.fields.get)
      val sortOrder = param(sortOrderKey)(requestParams).flatMap(bindSortDirection).getOrElse(SortDirection.ASCENDING)
      val teams     = params(teamsKey)(requestParams).getOrElse(Set.empty)
      val groupBy = orderedParams(groupByKey)(requestParams)
        .map(_.flatMap(ReportField.fields.get))
        .flatMap(NonEmptySeq.fromSeq)
      val caseTypes = params(caseTypesKey)(requestParams)
        .map(_.map(bindApplicationType).collect { case Some(value) => value })
        .getOrElse(Set.empty)
      val statuses = params(statusesKey)(requestParams)
        .map(_.map(bindPseudoCaseStatus).collect { case Some(status) => status })
        .getOrElse(Set.empty)
      val liabilityStatuses = params(liabilityStatusesKey)(requestParams)
        .map(_.map(bindLiabilityStatus).collect { case Some(status) => status })
        .getOrElse(Set.empty)
      val maxFields = orderedParams(maxFieldsKey)(requestParams)
        .map(_.flatMap(ReportField.fields.get(_).collect[ReportField[Long]] {
          case days @ DaysSinceField(_, _) => days
          case num @ NumberField(_, _)     => num
        }))
        .getOrElse(Seq.empty)

      (groupBy, sortBy).mapN { case (groupBy, sortBy) =>
        for {
          range   <- dateRange
          include <- includeCases
        } yield SummaryReport(
          dateRange = range,
          groupBy = groupBy,
          sortBy = sortBy,
          sortOrder = sortOrder,
          caseTypes = caseTypes,
          statuses = statuses,
          liabilityStatuses = liabilityStatuses,
          teams = teams,
          maxFields = maxFields,
          includeCases = include
        )
      }
    }

    override def unbind(key: String, value: SummaryReport): String =
      Seq(
        stringBindable.unbind(groupByKey, value.groupBy.map(_.fieldName).mkString_(",")),
        stringBindable.unbind(sortByKey, value.sortBy.fieldName),
        stringBindable.unbind(sortOrderKey, value.sortOrder.toString),
        stringBindable.unbind(caseTypesKey, value.caseTypes.map(_.toString).mkString(",")),
        stringBindable.unbind(statusesKey, value.statuses.map(_.toString).mkString(",")),
        stringBindable.unbind(liabilityStatusesKey, value.liabilityStatuses.map(_.toString).mkString(",")),
        stringBindable.unbind(teamsKey, value.teams.mkString(",")),
        rangeBindable.unbind(dateRangeKey, value.dateRange),
        stringBindable.unbind(maxFieldsKey, value.maxFields.map(_.fieldName).mkString(",")),
        boolBindable.unbind(includeCasesKey, value.includeCases)
      ).mkString("&")
  }
}

case class CaseReport(
  fields: NonEmptySeq[ReportField[_]],
  sortBy: ReportField[_] = ReportField.Reference,
  sortOrder: SortDirection.Value = SortDirection.ASCENDING,
  caseTypes: Set[ApplicationType.Value] = Set.empty,
  statuses: Set[PseudoCaseStatus.Value] = Set.empty,
  liabilityStatuses: Set[LiabilityStatus.Value] = Set.empty,
  teams: Set[String] = Set.empty,
  dateRange: InstantRange = InstantRange.allTime,
  dueToExpireRange: Boolean = false
) extends Report

object CaseReport {
  private val dateRangeKey         = "date"
  private val sortByKey            = "sort_by"
  private val sortOrderKey         = "sort_order"
  private val caseTypesKey         = "case_type"
  private val teamsKey             = "team"
  private val fieldsKey            = "fields"
  private val statusesKey          = "status"
  private val liabilityStatusesKey = "liability_status"
  private val dueToExpireReportKey = "due_to_expire"

  implicit def caseReportQueryStringBindable(implicit
    stringBindable: QueryStringBindable[String],
    rangeBindable: QueryStringBindable[InstantRange],
    booleanBindable: QueryStringBindable[Boolean]
  ): QueryStringBindable[CaseReport] = new QueryStringBindable[CaseReport] {
    import uk.gov.hmrc.bindingtariffclassification.model.utils.BinderUtil._

    override def bind(key: String, requestParams: Map[String, Seq[String]]): Option[Either[String, CaseReport]] = {
      val sortBy    = param(sortByKey)(requestParams).flatMap(ReportField.fields.get).getOrElse(ReportField.Reference)
      val sortOrder = param(sortOrderKey)(requestParams).flatMap(bindSortDirection).getOrElse(SortDirection.ASCENDING)
      val dateRange = rangeBindable.bind(dateRangeKey, requestParams).getOrElse(Right(InstantRange.allTime))
      val teams     = params(teamsKey)(requestParams).getOrElse(Set.empty)
      val caseTypes = params(caseTypesKey)(requestParams)
        .map(_.map(bindApplicationType).collect { case Some(value) => value })
        .getOrElse(Set.empty)
      val statuses = params(statusesKey)(requestParams)
        .map(_.map(bindPseudoCaseStatus).collect { case Some(status) => status })
        .getOrElse(Set.empty)
      val liabilityStatuses = params(liabilityStatusesKey)(requestParams)
        .map(_.map(bindLiabilityStatus).collect { case Some(status) => status })
        .getOrElse(Set.empty)
      val fields = orderedParams(fieldsKey)(requestParams)
        .map(_.flatMap(ReportField.fields.get))
        .flatMap(NonEmptySeq.fromSeq)
      val dueToExpire = booleanBindable.bind(dueToExpireReportKey, requestParams).getOrElse(Right(false))

      fields.map { fields =>
        for {
          range <- dateRange
          dte   <- dueToExpire
        } yield CaseReport(
          sortBy = sortBy,
          sortOrder = sortOrder,
          caseTypes = caseTypes,
          statuses = statuses,
          liabilityStatuses = liabilityStatuses,
          teams = teams,
          dateRange = range,
          fields = fields,
          dueToExpireRange = dte
        )
      }
    }

    override def unbind(key: String, value: CaseReport): String =
      Seq(
        stringBindable.unbind(sortByKey, value.sortBy.fieldName),
        stringBindable.unbind(sortOrderKey, value.sortOrder.toString),
        stringBindable.unbind(caseTypesKey, value.caseTypes.map(_.toString).mkString(",")),
        stringBindable.unbind(statusesKey, value.statuses.map(_.toString).mkString(",")),
        stringBindable.unbind(liabilityStatusesKey, value.liabilityStatuses.map(_.toString).mkString(",")),
        stringBindable.unbind(teamsKey, value.teams.mkString(",")),
        rangeBindable.unbind(dateRangeKey, value.dateRange),
        stringBindable.unbind(fieldsKey, value.fields.map(_.fieldName).mkString_(",")),
        booleanBindable.unbind(dueToExpireReportKey, value.dueToExpireRange)
      ).mkString("&")
  }
}

case class QueueReport(
  sortBy: ReportField[_] = ReportField.Team,
  sortOrder: SortDirection.Value = SortDirection.ASCENDING,
  caseTypes: Set[ApplicationType.Value] = Set.empty,
  statuses: Set[PseudoCaseStatus.Value] = Set.empty,
  liabilityStatuses: Set[LiabilityStatus.Value] = Set.empty,
  teams: Set[String] = Set.empty,
  assignee: Option[String] = Option.empty,
  dateRange: InstantRange = InstantRange.allTime,
  dueToExpireRange: Boolean = false
) extends Report

object QueueReport {
  private val dateRangeKey         = "date"
  private val sortByKey            = "sort_by"
  private val sortOrderKey         = "sort_order"
  private val caseTypesKey         = "case_type"
  private val teamsKey             = "team"
  private val statusesKey          = "status"
  private val assigneeKey          = "assigned_user"
  private val liabilityStatusesKey = "liability_status"

  implicit def queueReportQueryStringBindable(implicit
    stringBindable: QueryStringBindable[String],
    rangeBindable: QueryStringBindable[InstantRange]
  ): QueryStringBindable[QueueReport] = new QueryStringBindable[QueueReport] {
    import uk.gov.hmrc.bindingtariffclassification.model.utils.BinderUtil._

    override def bind(key: String, requestParams: Map[String, Seq[String]]): Option[Either[String, QueueReport]] = {
      val sortBy    = param(sortByKey)(requestParams).flatMap(ReportField.fields.get(_)).getOrElse(ReportField.Team)
      val sortOrder = param(sortOrderKey)(requestParams).flatMap(bindSortDirection).getOrElse(SortDirection.ASCENDING)
      val dateRange = rangeBindable.bind(dateRangeKey, requestParams).getOrElse(Right(InstantRange.allTime))
      val teams     = params(teamsKey)(requestParams).getOrElse(Set.empty)
      val assignee  = param(assigneeKey)(requestParams)
      val caseTypes = params(caseTypesKey)(requestParams)
        .map(_.map(bindApplicationType).collect { case Some(value) => value })
        .getOrElse(Set.empty)
      val statuses = params(statusesKey)(requestParams)
        .map(_.map(bindPseudoCaseStatus).collect { case Some(status) => status })
        .getOrElse(Set.empty)
      val liabilityStatuses = params(liabilityStatusesKey)(requestParams)
        .map(_.map(bindLiabilityStatus).collect { case Some(status) => status })
        .getOrElse(Set.empty)
      Some(
        for {
          range <- dateRange
        } yield QueueReport(
          sortBy = sortBy,
          sortOrder = sortOrder,
          caseTypes = caseTypes,
          statuses = statuses,
          liabilityStatuses = liabilityStatuses,
          teams = teams,
          dateRange = range,
          assignee = assignee
        )
      )
    }

    override def unbind(key: String, value: QueueReport): String =
      Seq(
        stringBindable.unbind(sortByKey, value.sortBy.fieldName),
        stringBindable.unbind(sortOrderKey, value.sortOrder.toString),
        stringBindable.unbind(caseTypesKey, value.caseTypes.map(_.toString).mkString(",")),
        stringBindable.unbind(statusesKey, value.statuses.map(_.toString).mkString(",")),
        stringBindable.unbind(liabilityStatusesKey, value.liabilityStatuses.map(_.toString).mkString(",")),
        stringBindable.unbind(teamsKey, value.teams.mkString(",")),
        rangeBindable.unbind(dateRangeKey, value.dateRange),
        value.assignee.map(stringBindable.unbind(assigneeKey, _)).getOrElse("")
      ).filterNot(_.isEmpty).mkString("&")
  }
}
