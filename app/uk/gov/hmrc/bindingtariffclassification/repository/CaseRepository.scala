/*
 * Copyright 2022 HM Revenue & Customs
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

import cats.data.NonEmptySeq

import java.time.Instant
import javax.inject.{Inject, Singleton}
import cats.syntax.all._
import play.api.libs.json._
import play.api.libs.json.Json.JsValueWrapper
import reactivemongo.api.indexes.Index
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.crypto.Crypto
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.reporting._
import uk.gov.hmrc.bindingtariffclassification.sort.SortDirection
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CaseRepository {

  def insert(c: Case): Future[Case]

  def update(c: Case, upsert: Boolean): Future[Option[Case]]

  def update(reference: String, caseUpdate: CaseUpdate): Future[Option[Case]]

  def getByReference(reference: String): Future[Option[Case]]

  def get(search: CaseSearch, pagination: Pagination): Future[Paged[Case]]

  def getAllByEori(eori: String): Future[List[Case]]

  def deleteAll(): Future[Unit]

  def delete(reference: String): Future[Unit]

  def summaryReport(report: SummaryReport, pagination: Pagination): Future[Paged[ResultGroup]]

  def caseReport(report: CaseReport, pagination: Pagination): Future[Paged[Map[String, ReportResultField[_]]]]

  def queueReport(report: QueueReport, pagination: Pagination): Future[Paged[QueueResultGroup]]
}

@Singleton
class EncryptedCaseMongoRepository @Inject() (repository: CaseMongoRepository, crypto: Crypto) extends CaseRepository {

  private def encrypt: Case => Case = crypto.encrypt

  private def decrypt: Case => Case = crypto.decrypt

  override def insert(c: Case): Future[Case] = repository.insert(encrypt(c)).map(decrypt)

  override def update(c: Case, upsert: Boolean): Future[Option[Case]] =
    repository.update(encrypt(c), upsert).map(_.map(decrypt))

  override def update(reference: String, caseUpdate: CaseUpdate): Future[Option[Case]] =
    repository.update(reference, caseUpdate).map(_.map(decrypt))

  override def getByReference(reference: String): Future[Option[Case]] =
    repository.getByReference(reference).map(_.map(decrypt))

  override def get(search: CaseSearch, pagination: Pagination): Future[Paged[Case]] =
    repository.get(enryptSearch(search), pagination).map(_.map(decrypt))

  override def getAllByEori(eori: String): Future[List[Case]] = {
    repository.getAllByEori(crypto.encryptString.apply(eori)).map(_.map(decrypt))
  }

  override def deleteAll(): Future[Unit] = repository.deleteAll()

  override def delete(reference: String): Future[Unit] = repository.delete(reference)

  private def enryptSearch(search: CaseSearch) = {
    val eoriEnc: Option[String] = search.filter.eori.map(crypto.encryptString)
    search.copy(filter = search.filter.copy(eori = eoriEnc))
  }

  override def summaryReport(report: SummaryReport, pagination: Pagination): Future[Paged[ResultGroup]] =
    repository.summaryReport(report, pagination)

  override def caseReport(
    report: CaseReport,
    pagination: Pagination
  ): Future[Paged[Map[String, ReportResultField[_]]]] =
    repository.caseReport(report, pagination)

  override def queueReport(
    report: QueueReport,
    pagination: Pagination
  ): Future[Paged[QueueResultGroup]] =
    repository.queueReport(report, pagination)
}

@Singleton
class CaseMongoRepository @Inject() (
  appConfig: AppConfig,
  mongoDbProvider: MongoDbProvider,
  mapper: SearchMapper,
  updateMapper: UpdateMapper
) extends ReactiveRepository[Case, BSONObjectID](
      collectionName = "cases",
      mongo          = mongoDbProvider.mongo,
      domainFormat   = MongoFormatters.formatCase
    )
    with CaseRepository
    with MongoCrudHelper[Case] {

  override val mongoCollection: JSONCollection = collection

  lazy private val uniqueSingleFieldIndexes = Seq("reference")
  lazy private val nonUniqueSingleFieldIndexes = Seq(
    "assignee.id",
    "queueId",
    "status",
    "application.holder.eori",
    "application.agent.eoriDetails.eori",
    "decision.effectiveEndDate",
    "decision.bindingCommodityCode",
    "daysElapsed",
    "keywords"
  )

  override def indexes: Seq[Index] =
    // TODO: We need to add relevant indexes for each possible search
    // TODO: We should add compound indexes for searches involving multiple fields
    uniqueSingleFieldIndexes.map(createSingleFieldAscendingIndex(_, isUnique      = true)) ++
      nonUniqueSingleFieldIndexes.map(createSingleFieldAscendingIndex(_, isUnique = false))

  override def insert(c: Case): Future[Case] =
    createOne(c)

  override def update(c: Case, upsert: Boolean): Future[Option[Case]] =
    updateDocument(
      selector = mapper.reference(c.reference),
      update   = c,
      upsert   = upsert
    )

  override def update(reference: String, caseUpdate: CaseUpdate): Future[Option[Case]] =
    collection
      .findAndUpdate(
        selector       = mapper.reference(reference),
        update         = updateMapper.updateCase(caseUpdate),
        fetchNewObject = true
      )
      .map(_.value.map(_.as[Case]))

  override def getByReference(reference: String): Future[Option[Case]] =
    getOne(selector = mapper.reference(reference))

  override def get(search: CaseSearch, pagination: Pagination): Future[Paged[Case]] =
    getMany(
      filterBy = mapper.filterBy(search.filter),
      sortBy   = search.sort.map(mapper.sortBy).getOrElse(Json.obj()),
      pagination
    )

  override def getAllByEori(eori: String): Future[List[Case]] = {
    getAll(Json.obj("application.holder.eori" -> eori))
  }

  override def deleteAll(): Future[Unit] =
    removeAll().map(_ => ())

  override def delete(reference: String): Future[Unit] =
    remove("reference" -> reference).map(_ => ())

  private def greaterThan(json: JsValueWrapper): JsObject =
    Json.obj("$gte" -> json)

  private def lessThan(json: JsValueWrapper): JsObject =
    Json.obj("$lte" -> json)

  private def trunc(json: JsValueWrapper): JsObject =
    Json.obj("$trunc" -> json)

  private def divide(dividend: JsValueWrapper, divisor: JsValueWrapper): JsObject =
    Json.obj("$divide" -> Json.arr(dividend, divisor))

  private def subtract(minuend: JsValueWrapper, subtrahend: JsValueWrapper): JsObject =
    Json.obj("$subtract" -> Json.arr(minuend, subtrahend))

  private def substrBytes(operand: JsValueWrapper, offset: JsValueWrapper, length: JsValueWrapper): JsObject =
    Json.obj("$substrBytes" -> Json.arr(operand, offset, length))

  private def and(operands: JsValueWrapper*): JsObject =
    Json.obj("$and" -> Json.arr(operands: _*))

  private def eq(lExpr: JsValueWrapper, rExpr: JsValueWrapper): JsObject =
    Json.obj("$eq" -> Json.arr(lExpr, rExpr))

  private def in(expr: JsValueWrapper, arrayExpr: JsValueWrapper): JsObject =
    Json.obj("$in" -> Json.arr(expr, arrayExpr))

  private def in(arrayExpr: JsValueWrapper): JsObject =
    Json.obj("$in" -> arrayExpr)

  private def not(operand: JsValueWrapper): JsObject =
    Json.obj("$not" -> operand)

  private def elemMatch(conditions: JsValueWrapper): JsObject =
    Json.obj("$elemMatch" -> conditions)

  private def notEmpty(operand: JsValueWrapper): JsObject =
    Json.obj("$gt" -> Json.arr(Json.obj("$size" -> operand), 0))

  private def filter(input: JsValueWrapper, cond: JsValueWrapper): JsObject =
    Json.obj("$filter" -> Json.obj("input" -> input, "cond" -> cond))

  private def daysSince(operand: JsValueWrapper): JsValue =
    trunc(
      divide(
        subtract(Json.toJson(appConfig.clock.instant()), operand),
        86400000
      )
    )

  private def notNull(operandExpr: JsValueWrapper): JsValue =
    Json.obj("$gt" -> Json.arr(operandExpr, JsNull))

  private def cond(ifExpr: JsValueWrapper, thenExpr: JsValueWrapper, elseExpr: JsValueWrapper): JsValue =
    Json.obj(
      "$cond" -> Json.obj(
        "if"   -> ifExpr,
        "then" -> thenExpr,
        "else" -> elseExpr
      )
    )

  private def pseudoStatus(): JsValue = {
    val time        = Json.toJson(appConfig.clock.instant())
    val statusField = s"$$${ReportField.Status.underlyingField}"

    val isAppeal = and(
      eq(s"$$${ReportField.Status.underlyingField}", CaseStatus.COMPLETED.toString),
      notNull("$decision.appeal"),
      notEmpty(
        filter(
          input = "$decision.appeal",
          cond  = in("$$this.type", AppealType.appealTypes.map(Json.toJson(_)))
        )
      )
    )

    val isReview = and(
      eq(s"$$${ReportField.Status.underlyingField}", CaseStatus.COMPLETED.toString),
      notNull("$decision.appeal"),
      notEmpty(
        filter(
          input = "$decision.appeal",
          cond  = in("$$this.type", AppealType.reviewTypes.map(Json.toJson(_)))
        )
      )
    )

    val isExpired = and(
      eq(s"$$${ReportField.Status.underlyingField}", CaseStatus.COMPLETED.toString),
      notNull(s"$$${ReportField.DateExpired.underlyingField}"),
      greaterThan(Json.arr(time, s"$$${ReportField.DateExpired.underlyingField}"))
    )

    cond(
      ifExpr   = isAppeal,
      thenExpr = Json.toJson(PseudoCaseStatus.UNDER_APPEAL),
      elseExpr = cond(
        ifExpr   = isReview,
        thenExpr = Json.toJson(PseudoCaseStatus.UNDER_REVIEW),
        elseExpr = cond(
          ifExpr   = isExpired,
          thenExpr = Json.toJson(PseudoCaseStatus.EXPIRED),
          elseExpr = statusField
        )
      )
    )
  }

  private def coalesce(fieldChoices: NonEmptySeq[String]): JsValue =
    fieldChoices.init.foldRight(JsString("$" + fieldChoices.last): JsValue) {
      case (field, expr) => Json.obj("$ifNull" -> Json.arr("$" + field, expr))
    }

  private def matchStage(framework: collection.AggregationFramework, report: Report) = {
    import framework._

    val GatewayTeamId = "1"

    val caseTypeFilter =
      if (report.caseTypes.isEmpty)
        Json.obj()
      else
        Json.obj(ReportField.CaseType.underlyingField -> Json.obj("$in" -> report.caseTypes.map(Json.toJson(_))))

    val statusFilter =
      if (report.statuses.isEmpty)
        Json.obj()
      else {
        val (concreteStatuses, pseudoStatuses) = report.statuses.partition(p => CaseStatus.fromPseudoStatus(p).nonEmpty)

        val concreteFilter = Json.arr(
          Json.obj(ReportField.Status.underlyingField -> Json.obj("$in" -> concreteStatuses.map(Json.toJson(_))))
        )

        val pseudoFilters = Json.arr(
          pseudoStatuses.collect {
            case PseudoCaseStatus.EXPIRED =>
              Json.obj(
                ReportField.Status.underlyingField      -> Json.toJson(PseudoCaseStatus.COMPLETED),
                ReportField.DateExpired.underlyingField -> lessThan(appConfig.clock.instant()),
                "decision.appeal"                       -> Json.obj("$size" -> 0)
              ): JsValueWrapper
            case PseudoCaseStatus.UNDER_APPEAL =>
              Json.obj(
                ReportField.Status.underlyingField -> Json.toJson(PseudoCaseStatus.COMPLETED),
                "decision.appeal" -> elemMatch(
                  Json.obj("type" -> in(AppealType.appealTypes.map(Json.toJson(_))))
                )
              ): JsValueWrapper
            case PseudoCaseStatus.UNDER_REVIEW =>
              Json.obj(ReportField.Status.underlyingField -> Json.toJson(PseudoCaseStatus.COMPLETED)) ++ and(
                Json.obj(
                  "decision.appeal" -> not(
                    elemMatch(
                      Json.obj("type" -> in(AppealType.appealTypes.map(Json.toJson(_))))
                    )
                  )
                ),
                Json.obj(
                  "decision.appeal" -> elemMatch(
                    Json.obj(
                      "type" -> in(AppealType.reviewTypes.map(Json.toJson(_)))
                    )
                  )
                )
              ): JsValueWrapper
          }.toSeq: _*
        )

        Json.obj("$or" -> (concreteFilter ++ pseudoFilters))
      }

    val liabilityStatusesFilter =
      if (report.liabilityStatuses.isEmpty)
        Json.obj()
      else {
        Json.obj(
          ReportField.LiabilityStatus.underlyingField -> Json.obj("$in" -> report.liabilityStatuses.map(Json.toJson(_)))
        )
      }

    val teamFilter =
      if (report.teams.isEmpty)
        Json.obj()
      else if (report.teams.exists(_ == GatewayTeamId))
        Json.obj(
          ReportField.Team.underlyingField -> Json
            .obj("$in" -> JsArray(JsNull :: report.teams.toList.filterNot(_ == GatewayTeamId).map(Json.toJson(_))))
        )
      else
        Json.obj(ReportField.Team.underlyingField -> Json.obj("$in" -> report.teams))

    val minDateFilter =
      if (report.dateRange.min == Instant.MIN)
        Json.obj()
      else
        Json.obj("$gte" -> Json.toJson(report.dateRange.min))

    val maxDateFilter =
      if (report.dateRange.max == Instant.MAX)
        Json.obj()
      else
        Json.obj("$lte" -> Json.toJson(report.dateRange.max))

    val dateFilter =
      if (report.dateRange == InstantRange.allTime)
        Json.obj()
      else
        Json.obj(ReportField.DateCreated.underlyingField -> (minDateFilter ++ maxDateFilter))

    val assigneeFilter = report match {
      case _: CaseReport =>
        Json.obj()
      case _: SummaryReport =>
        Json.obj()
      case queue: QueueReport =>
        queue.assignee.map(assignee => Json.obj(ReportField.User.underlyingField -> assignee)).getOrElse {
          Json.obj(ReportField.User.underlyingField -> JsNull)
        }
    }

    Match(caseTypeFilter ++ statusFilter ++ teamFilter ++ dateFilter ++ assigneeFilter ++ liabilityStatusesFilter)
  }

  private def summarySortStage(
    framework: collection.AggregationFramework,
    report: SummaryReport
  ) = {
    import framework._

    val sortField = report match {
      case summary: SummaryReport if summary.groupBy.toSeq.contains(report.sortBy) =>
        s"groupKey.${report.sortBy.fieldName}"
      case _ =>
        report.sortBy.fieldName
    }

    report.sortOrder match {
      case SortDirection.ASCENDING =>
        Sort(Ascending(sortField))
      case SortDirection.DESCENDING =>
        Sort(Descending(sortField))
    }
  }

  private def sortStage(
    framework: collection.AggregationFramework,
    sortBy: ReportField[_],
    sortOrder: SortDirection.Value
  ) = {
    import framework._

    // If not sorting by reference, add it as a secondary sort field to ensure stable sorting
    (sortOrder, sortBy) match {
      case (SortDirection.ASCENDING, ReportField.Reference) =>
        Sort(Ascending(sortBy.underlyingField))
      case (SortDirection.DESCENDING, ReportField.Reference) =>
        Sort(Descending(sortBy.underlyingField))
      case (SortDirection.ASCENDING, _) =>
        Sort(Ascending(sortBy.underlyingField), Ascending(ReportField.Reference.underlyingField))
      case (SortDirection.DESCENDING, _) =>
        Sort(Descending(sortBy.underlyingField), Descending(ReportField.Reference.underlyingField))
    }
  }

  private def groupStage(framework: collection.AggregationFramework, report: SummaryReport) = {
    import framework._

    val countField = Seq(ReportField.Count.fieldName -> SumAll)

    val casesField = if (report.includeCases) Seq("cases" -> PushField("$ROOT")) else Seq.empty

    val maxFields =
      report.maxFields.toList.map {
        case DaysSinceField(fieldName, underlyingField) =>
          fieldName -> Max(daysSince(s"$$$underlyingField"))
        case field =>
          field.fieldName -> MaxField(field.underlyingField)
      }

    val groupFields = countField ++ maxFields ++ casesField

    val groupBy = Json.obj(report.groupBy.map {
      case ChapterField(fieldName, underlyingField) =>
        fieldName -> (substrBytes(s"$$$underlyingField", 0, 2): JsValueWrapper)
      case DaysSinceField(fieldName, underlyingField) =>
        fieldName -> (daysSince(s"$$$underlyingField"): JsValueWrapper)
      case StatusField(fieldName, _) =>
        fieldName -> (pseudoStatus(): JsValueWrapper)
      case CoalesceField(fieldName, fieldChoices) =>
        fieldName -> (coalesce(fieldChoices): JsValueWrapper)
      case field =>
        field.fieldName -> (JsString(s"$$${field.underlyingField}"): JsValueWrapper)
    }.toSeq: _*)

    Group(groupBy)(groupFields: _*)
  }

  private def getFieldValue(field: ReportField[_], json: Option[JsValue]): ReportResultField[_] = field match {
    case field @ CaseTypeField(_, _)        => field.withValue(json.flatMap(_.asOpt[ApplicationType.Value]))
    case field @ ChapterField(_, _)         => field.withValue(json.flatMap(_.asOpt[String].filterNot(_.isEmpty)))
    case field @ DateField(_, _)            => field.withValue(json.flatMap(_.asOpt[Instant]))
    case field @ DaysSinceField(_, _)       => field.withValue(json.flatMap(_.asOpt[Long]))
    case field @ NumberField(_, _)          => field.withValue(json.flatMap(_.asOpt[Long]))
    case field @ StatusField(_, _)          => field.withValue(json.flatMap(_.asOpt[PseudoCaseStatus.Value]))
    case field @ LiabilityStatusField(_, _) => field.withValue(json.flatMap(_.asOpt[LiabilityStatus.Value]))
    case field @ StringField(_, _)          => field.withValue(json.flatMap(_.asOpt[String]))
    case field @ CoalesceField(_, _)        => field.withValue(json.flatMap(_.asOpt[String].filterNot(_.isEmpty)))
  }

  private def getNumberFieldValue(field: ReportField[Long], json: Option[JsValue]): NumberResultField = field match {
    case field @ DaysSinceField(_, _) => field.withValue(json.flatMap(_.asOpt[Long]))
    case field @ NumberField(_, _)    => field.withValue(json.flatMap(_.asOpt[Long]))
  }

  override def summaryReport(report: SummaryReport, pagination: Pagination): Future[Paged[ResultGroup]] = {
    logger.info(s"Running report: $report with pagination $pagination")

    val countField = "resultCount"

    val runCount = collection
      .aggregateWith[JsObject](allowDiskUse = true) { framework =>
        import framework._

        val first = matchStage(framework, report)

        val rest = List(
          groupStage(framework, report),
          Count(countField)
        )

        (first, rest)

      }
      .headOption

    val runAggregation = collection
      .aggregateWith[JsObject](allowDiskUse = true) { framework =>
        import framework._

        val first = matchStage(framework, report)

        val rest = List(
          sortStage(framework, ReportField.Reference, SortDirection.ASCENDING),
          groupStage(framework, report),
          AddFields(Json.obj("groupKey" -> "$_id")),
          Project(Json.obj("_id"        -> 0)),
          summarySortStage(framework, report),
          Skip((pagination.page - 1) * pagination.pageSize),
          Limit(pagination.pageSize)
        )

        (first, rest)

      }
      .fold[Seq[ResultGroup]](Seq.empty, pagination.pageSize) {
        case (rows, json) =>
          rows ++ Seq(
            if (report.includeCases)
              CaseResultGroup(
                count = json(ReportField.Count.fieldName).as[Long],
                groupKey = report.groupBy
                  .map(groupBy => getFieldValue(groupBy, (json \ "groupKey" \ groupBy.fieldName).toOption)),
                maxFields =
                  report.maxFields.map(field => getNumberFieldValue(field, json.value.get(field.fieldName))).toList,
                cases = json("cases").as[List[Case]]
              )
            else
              SimpleResultGroup(
                count = json(ReportField.Count.fieldName).as[Long],
                groupKey = report.groupBy
                  .map(groupBy => getFieldValue(groupBy, (json \ "groupKey" \ groupBy.fieldName).toOption)),
                maxFields =
                  report.maxFields.map(field => getNumberFieldValue(field, json.value.get(field.fieldName))).toList
              )
          )
      }

    (runCount, runAggregation).mapN {
      case (count, results) =>
        Paged(results, pagination, count.map(_(countField).as[Int]).getOrElse(0))
    }
  }

  override def caseReport(
    report: CaseReport,
    pagination: Pagination
  ): Future[Paged[Map[String, ReportResultField[_]]]] = {
    logger.info(s"Running report: $report with pagination $pagination")

    val countField = "resultCount"

    val runCount = collection
      .aggregateWith[JsObject](allowDiskUse = true) { framework =>
        import framework._

        val first = matchStage(framework, report)

        val rest = List(Count(countField))

        (first, rest)

      }
      .headOption

    val runAggregation = collection
      .aggregateWith[JsObject](allowDiskUse = true) { framework =>
        import framework._

        val fields = Json.obj(
          report.fields.toList.map {
            case ChapterField(fieldName, underlyingField) =>
              fieldName -> (substrBytes(s"$$$underlyingField", 0, 2): JsValueWrapper)
            case DaysSinceField(fieldName, underlyingField) =>
              fieldName -> (daysSince(s"$$$underlyingField"): JsValueWrapper)
            case StatusField(fieldName, _) =>
              fieldName -> (pseudoStatus(): JsValueWrapper)
            case CoalesceField(fieldName, fieldChoices) =>
              fieldName -> (coalesce(fieldChoices): JsValueWrapper)
            case field =>
              field.fieldName -> (s"$$${field.underlyingField}": JsValueWrapper)
          }: _*
        )

        val first = matchStage(framework, report)

        val rest = List(
          sortStage(framework, report.sortBy, report.sortOrder),
          AddFields(fields),
          Project(Json.obj("_id" -> JsNumber(0))),
          Skip((pagination.page - 1) * pagination.pageSize),
          Limit(pagination.pageSize)
        )

        (first, rest)

      }
      .fold[Seq[Map[String, ReportResultField[_]]]](Seq.empty, pagination.pageSize) {
        case (rows, json) =>
          rows ++ Seq(
            report.fields.toSeq
              .map(field => field.fieldName -> getFieldValue(field, json.value.get(field.fieldName)))
              .toMap[String, ReportResultField[_]]
          )
      }

    (runCount, runAggregation).mapN {
      case (count, results) =>
        Paged(results, pagination, count.map(_(countField).as[Int]).getOrElse(0))
    }
  }

  private def queueGroupStage(framework: collection.AggregationFramework, report: QueueReport) = {
    import framework._
    GroupMulti(
      ReportField.Team.fieldName     -> ReportField.Team.underlyingField,
      ReportField.CaseType.fieldName -> ReportField.CaseType.underlyingField
    )(ReportField.Count.fieldName -> SumAll)
  }

  private def queueSortStage(framework: collection.AggregationFramework, report: QueueReport) = {
    import framework._

    val sortDirection = (field: String) =>
      if (report.sortOrder == SortDirection.ASCENDING) Ascending(field) else Descending(field)

    def teamThenCaseType =
      Seq(sortDirection(s"_id.${ReportField.Team.fieldName}"), sortDirection(s"_id.${ReportField.CaseType.fieldName}"))

    def caseTypeThenTeam =
      Seq(sortDirection(s"_id.${ReportField.CaseType.fieldName}"), sortDirection(s"_id.${ReportField.Team.fieldName}"))

    def countThenTeamThenCaseType =
      sortDirection(ReportField.Count.fieldName) +: teamThenCaseType

    // Ideally we want to sort by both parts of the grouping key to improve sort stability
    report.sortBy match {
      case ReportField.Count    => Sort(countThenTeamThenCaseType: _*)
      case ReportField.CaseType => Sort(caseTypeThenTeam: _*)
      case ReportField.Team     => Sort(teamThenCaseType: _*)
    }
  }

  override def queueReport(
    report: QueueReport,
    pagination: Pagination
  ): Future[Paged[QueueResultGroup]] = {
    logger.info(s"Running report: $report with pagination $pagination")

    val countField = "resultCount"

    val runCount = collection
      .aggregateWith[JsObject](allowDiskUse = true) { framework =>
        import framework._

        val first = matchStage(framework, report)

        val rest = List(
          queueGroupStage(framework, report),
          Count(countField)
        )

        (first, rest)

      }
      .headOption

    val runAggregation = collection
      .aggregateWith[JsObject](allowDiskUse = true) { framework =>
        import framework._

        val first = matchStage(framework, report)

        val rest = List(
          queueGroupStage(framework, report),
          queueSortStage(framework, report),
          Skip((pagination.page - 1) * pagination.pageSize),
          Limit(pagination.pageSize)
        )

        (first, rest)

      }
      .fold[Seq[QueueResultGroup]](Seq.empty, pagination.pageSize) {
        case (rows, json) =>
          rows ++ Seq(
            QueueResultGroup(
              count    = json(ReportField.Count.fieldName).as[Int],
              team     = json("_id").as[JsObject].value.get(ReportField.Team.fieldName).flatMap(_.asOpt[String]),
              caseType = json("_id").as[JsObject].apply(ReportField.CaseType.fieldName).as[ApplicationType.Value]
            )
          )
      }

    (runCount, runAggregation).mapN {
      case (count, results) =>
        Paged(results, pagination, count.map(_(countField).as[Int]).getOrElse(0))
    }
  }
}
