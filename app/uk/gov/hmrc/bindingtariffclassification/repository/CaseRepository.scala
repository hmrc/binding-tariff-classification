/*
 * Copyright 2021 HM Revenue & Customs
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
import cats.syntax.all._
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.{BsonArray, BsonDateTime, BsonDocument, BsonNull, BsonNumber, BsonString, BsonValue}
import org.mongodb.scala.model.Accumulators._
import org.mongodb.scala.model.Aggregates._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Sorts.{ascending, _}
import org.mongodb.scala.model.{Aggregates, Field, IndexModel, IndexOptions}
import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.crypto.Crypto
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.reporting._
import uk.gov.hmrc.bindingtariffclassification.repository.CaseRepository.caseIndexes
import uk.gov.hmrc.bindingtariffclassification.sort.SortDirection
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.Instant
import java.util
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//TODO refactor entire class, split into separate components, make more readable

@Singleton
class EncryptedCaseMongoRepository @Inject() (repository: CaseRepository, crypto: Crypto) {

  private def encrypt: Case => Case = crypto.encrypt

  private def decrypt: Case => Case = crypto.decrypt

  def insertCase(c: Case): Future[Case] = repository.insertOne(encrypt(c)).map(decrypt)

  def updateCase(c: Case, upsert: Boolean): Future[Option[Case]] =
    repository.updateCase(encrypt(c), upsert).map(_.map(decrypt))

  def updateCase(reference: String, caseUpdate: CaseUpdate): Future[Option[Case]] =
    repository.updateCase(reference, caseUpdate).map(_.map(decrypt))

  def getCase(reference: String): Future[Option[Case]] =
    repository.findOne(equal("reference", reference)).map(_.map(decrypt))

  def getCases(search: CaseSearch, pagination: Pagination): Future[Paged[Case]] =
    repository.getCases(enryptSearch(search), pagination).map(_.map(decrypt))

  def delete(reference: String): Future[Unit] = repository.deleteCase(reference)

  private def enryptSearch(search: CaseSearch) = {
    val eoriEnc: Option[String] = search.filter.eori.map(crypto.encryptString)
    search.copy(filter = search.filter.copy(eori = eoriEnc))
  }

  def summaryReport(report: SummaryReport, pagination: Pagination): Future[Paged[ResultGroup]] =
    repository.summaryReport(report, pagination)

  def caseReport(
                           report: CaseReport,
                           pagination: Pagination
                         ): Future[Paged[Map[String, ReportResultField[_]]]] =
    repository.caseReport(report, pagination)

  def queueReport(
                            report: QueueReport,
                            pagination: Pagination
                          ): Future[Paged[QueueResultGroup]] =
    repository.queueReport(report, pagination)
}

object CaseRepository {
  // TODO: We need to add relevant indexes for each possible search
  // TODO: We should add compound indexes for searches involving multiple fields
  lazy private val caseIndexes = Seq(
    "assignee.id",
    "queueId",
    "status",
    "application.holder.eori",
    "application.agent.eoriDetails.eori",
    "decision.effectiveEndDate",
    "decision.bindingCommodityCode",
    "daysElapsed",
    "keywords"
  ).map(name => IndexModel(ascending(name),IndexOptions().unique(false)))
    .+:( IndexModel(ascending("reference"),IndexOptions().unique(true)))
}

@Singleton
class CaseRepository @Inject() (appConfig: AppConfig, mongoComponent: MongoComponent, mapper: SearchMapper,
                                     updateMapper: UpdateMapper) extends PlayMongoRepository[Case](
  collectionName = "cases",
  mongoComponent = mongoComponent,
  domainFormat   = MongoFormatters.formatCase,
  indexes = caseIndexes)
  with MongoCrudHelper[Case] with Logging {

  override protected val mongoCollection: MongoCollection[Case] = collection

  def updateCase(`case`: Case, upsert: Boolean): Future[Option[Case]] = {
    updateOne(equal("reference", `case`.reference), `case`, upsert)
  }

  def updateCase(reference: String, caseUpdate: CaseUpdate): Future[Option[Case]] = {
    updateOne(equal("reference", reference), updateMapper.updateCase(caseUpdate))
  }

  def getCase(reference: String): Future[Option[Case]] =
    findOne(selector = equal("reference", reference))

  def getCases(search: CaseSearch, pagination: Pagination): Future[Paged[Case]] = {
    findMany(
      filterBy = BsonDocument(mapper.filterBy(search.filter).toString()),
      sortBy   = search.sort.map(mapper.sortBy).getOrElse(BsonDocument()),
      pagination
    )
  }

  def deleteCase(reference: String): Future[Unit] = deleteOne(equal("reference", reference))

  private def greaterThan(field: BsonValue) =
    BsonDocument("$gte" -> field)

  private def lessThan(field: BsonValue): BsonDocument =
    BsonDocument("$lte" -> field)

  private def trunc(field: BsonValue): BsonDocument =
    BsonDocument("$trunc" -> field)

  private def divide(dividend: BsonValue, divisor: Int): BsonDocument =
    BsonDocument("$divide" -> BsonArray.fromIterable(Seq(dividend, BsonNumber(divisor))))

  private def subtract(minuend: String, subtrahend: String): BsonDocument =
    BsonDocument("$subtract" -> BsonArray.fromIterable(Seq(BsonString(minuend), BsonString(subtrahend))))

  private def substrBytes(operand: String, offset: Int, length: Int): BsonDocument =
    BsonDocument("$substrBytes" -> BsonArray.fromIterable(Seq(BsonString(operand), BsonNumber(offset), BsonNumber(length))))

  private def and(operands: BsonValue*): BsonDocument =
    BsonDocument("$and" -> BsonArray.fromIterable(operands))

  private def eq(lExpr: String, rExpr: String): BsonDocument =
    BsonDocument("$eq" -> BsonArray.fromIterable(Seq(BsonString(lExpr), BsonString(rExpr))))

  private def in(expr: String, arrayExpr: String): BsonDocument =
    BsonDocument("$in" -> BsonArray.fromIterable(Seq(BsonString(expr), BsonString(arrayExpr))))

  private def in(arrayExpr: Set[JsValue]): BsonDocument =
    BsonDocument("$in" -> BsonArray.fromIterable(arrayExpr.map(i => BsonString(i.toString()))))

  private def in(expr: String, arrayExpr: Set[JsValue]): BsonDocument = {
    BsonDocument("$in" -> BsonArray.fromIterable(Set(BsonString(expr)).++(arrayExpr.map(i => BsonString(i.toString())))))
  }

  private def in(arrayExpr: String): BsonDocument =
    BsonDocument("$in" -> arrayExpr)

  private def not(operand: BsonDocument): BsonDocument =
    BsonDocument("$not" -> operand)

  private def elemMatch(conditions: BsonValue): BsonDocument =
    BsonDocument("$elemMatch" -> conditions)

  private def notEmpty(operand: BsonValue): BsonDocument =
    BsonDocument("$gt" -> BsonArray.fromIterable(Seq(BsonDocument("$size" -> operand), BsonNumber(0))))

  private def filter(input: String, cond: BsonValue): BsonDocument =
    BsonDocument("$filter" -> BsonDocument("input" -> input, "cond" -> cond))

  private def daysSince(operand: String): BsonDocument =
    trunc(
      divide(
        subtract(appConfig.clock.instant().toString, operand),
        86400000
      )
    )

  private def notNull(operandExpr: String): BsonDocument =
    BsonDocument("$gt" -> BsonArray.fromIterable(Seq(BsonString(operandExpr), BsonNull())))

  private def cond(ifExpr: BsonValue, thenExpr: BsonValue, elseExpr: BsonValue): BsonDocument =
    BsonDocument(
      "$cond" -> BsonDocument(
        "if"   -> ifExpr,
        "then" -> thenExpr,
        "else" -> elseExpr
      )
    )

  private def pseudoStatus(): BsonDocument = {
    val time        = BsonDateTime(appConfig.clock.instant().toEpochMilli)
    val statusField = s"$$${ReportField.Status.underlyingField}"

    val isAppeal = and(
      BsonDocument(s"$$${ReportField.Status.underlyingField}" -> BsonString(CaseStatus.COMPLETED.toString)),
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
      greaterThan(BsonArray.fromIterable(Seq(time,BsonString(s"$$${ReportField.DateExpired.underlyingField}"))))
    )

    cond(
      ifExpr   = isAppeal,
      thenExpr = BsonString(PseudoCaseStatus.UNDER_APPEAL.toString),
      elseExpr = cond(
        ifExpr   = isReview,
        thenExpr = BsonString(PseudoCaseStatus.UNDER_REVIEW.toString),
        elseExpr = cond(
          ifExpr   = isExpired,
          thenExpr = BsonString(PseudoCaseStatus.EXPIRED.toString),
          elseExpr = BsonString(statusField)
        )
      )
    )
  }

  private def coalesce(fieldChoices: NonEmptySeq[String]): BsonValue =
    fieldChoices.init.foldRight(BsonString("$" + fieldChoices.last): BsonValue) {
      case (field, expr) => BsonDocument("$ifNull" -> BsonArray.fromIterable(Seq(BsonDocument("$" + field -> expr))))
    }

  private def matchStage(report: Report) = {

    val GatewayTeamId = "1"

    val caseTypeFilter =
      if (report.caseTypes.isEmpty)
        BsonDocument()
      else
        BsonDocument(ReportField.CaseType.underlyingField -> BsonDocument("$in" -> BsonArray.fromIterable(report.caseTypes.map(i => BsonString(Json.toJson(i).toString())))))

    val statusFilter =
      if (report.statuses.isEmpty)
        BsonDocument()
      else {
        val (concreteStatuses, pseudoStatuses) = report.statuses.partition(p => CaseStatus.fromPseudoStatus(p).nonEmpty)

        val concreteFilter = BsonArray.fromIterable(Seq(
          BsonDocument(ReportField.Status.underlyingField -> BsonDocument("$in" -> BsonArray.fromIterable(concreteStatuses.map(i => BsonString(Json.toJson(i).toString())))))
        ))

        val pseudoFilters = BsonArray.fromIterable(
          pseudoStatuses.collect {
            case PseudoCaseStatus.EXPIRED =>
              BsonDocument(
                ReportField.Status.underlyingField      -> BsonString(Json.toJson(PseudoCaseStatus.COMPLETED).toString()),
                ReportField.DateExpired.underlyingField -> lessThan(BsonDateTime(appConfig.clock.instant().toEpochMilli)),
                "decision.appeal"                       -> BsonDocument("$size" -> 0)
              )
            case PseudoCaseStatus.UNDER_APPEAL =>
              BsonDocument(
                ReportField.Status.underlyingField -> BsonString(Json.toJson(PseudoCaseStatus.COMPLETED).toString()),
                "decision.appeal" -> elemMatch(
                  BsonDocument("type" -> in(AppealType.appealTypes.map(Json.toJson(_))))
                )
              )
            case PseudoCaseStatus.UNDER_REVIEW =>
              and(BsonDocument(ReportField.Status.underlyingField -> BsonString(Json.toJson(PseudoCaseStatus.COMPLETED).toString())),
                and(
                BsonDocument(
                  "decision.appeal" -> not(
                    elemMatch(
                      BsonDocument("type" -> in(AppealType.appealTypes.map(Json.toJson(_))))
                    )
                  )
                )),
                BsonDocument(
                  "decision.appeal" -> elemMatch(
                    BsonDocument(
                      "type" -> in(AppealType.reviewTypes.map(Json.toJson(_)))
                    )
                  )
                )
              )
          }.toSeq
        )

        val filter = concreteFilter
        filter.addAll(pseudoFilters)

        BsonDocument("$or" -> (filter))
      }

    val liabilityStatusesFilter =
      if (report.liabilityStatuses.isEmpty)
        BsonDocument()
      else {
        BsonDocument(
          ReportField.LiabilityStatus.underlyingField -> BsonDocument("$in" -> BsonArray.fromIterable(report.liabilityStatuses.map(i => BsonString(Json.toJson(i).toString()))))
        )
      }

    val teamFilter =
      if (report.teams.isEmpty)
        BsonDocument()
      else if (report.teams.contains(GatewayTeamId))
        BsonDocument(
          ReportField.Team.underlyingField -> BsonDocument("$in" -> BsonArray.fromIterable(Seq(BsonNull()) ++ report.teams.toList.filterNot(_ == GatewayTeamId).map(BsonString(_))))
        )
      else
        BsonDocument(ReportField.Team.underlyingField -> BsonDocument("$in" -> BsonArray.fromIterable(report.teams.map(BsonString(_)))))

    val minDateFilter =
      if (report.dateRange.min == Instant.MIN)
        BsonDocument()
      else
        BsonDocument("$gte" -> BsonDateTime(report.dateRange.min.toEpochMilli))

    val maxDateFilter =
      if (report.dateRange.max == Instant.MAX)
        BsonDocument()
      else
        BsonDocument("$lte" -> BsonDateTime(report.dateRange.max.toEpochMilli))

    val dateFilter =
      if (report.dateRange == InstantRange.allTime)
        BsonDocument()
      else
        BsonDocument(ReportField.DateCreated.underlyingField -> and(minDateFilter, maxDateFilter))

    val assigneeFilter = report match {
      case _: CaseReport =>
        BsonDocument()
      case _: SummaryReport =>
        BsonDocument()
      case queue: QueueReport =>
        queue.assignee.map(assignee => BsonDocument(ReportField.User.underlyingField -> assignee)).getOrElse {
          BsonDocument(ReportField.User.underlyingField -> BsonNull())
        }
    }

    `match`(and(caseTypeFilter, statusFilter, teamFilter, dateFilter, assigneeFilter, liabilityStatusesFilter))
  }

  private def summarySortStage(report: SummaryReport) = {

    val sortField = report match {
      case summary: SummaryReport if summary.groupBy.toSeq.contains(report.sortBy) =>
        s"groupKey.${report.sortBy.fieldName}"
      case _ =>
        report.sortBy.fieldName
    }

    report.sortOrder match {
      case SortDirection.ASCENDING =>
        sort(ascending(sortField))
      case SortDirection.DESCENDING =>
        sort(descending(sortField))
    }
  }

  private def sortStage(
                         sortBy: ReportField[_],
                         sortOrder: SortDirection.Value
                       ) = {
    // If not sorting by reference, add it as a secondary sort field to ensure stable sorting
    (sortOrder, sortBy) match {
      case (SortDirection.ASCENDING, ReportField.Reference) =>
        BsonDocument("$sort" -> BsonDocument(sortBy.underlyingField -> 1))
      case (SortDirection.DESCENDING, ReportField.Reference) =>
        BsonDocument("$sort" -> BsonDocument(sortBy.underlyingField -> -11))
      case (SortDirection.ASCENDING, _) =>
        BsonDocument("$sort" -> BsonArray.fromIterable(Seq(
          BsonDocument(sortBy.underlyingField -> 1),
          BsonDocument(ReportField.Reference.underlyingField -> 1))))
      case (SortDirection.DESCENDING, _) =>
        BsonDocument("$sort" -> BsonArray.fromIterable(Seq(
          BsonDocument(sortBy.underlyingField -> -1),
          BsonDocument(ReportField.Reference.underlyingField -> -1))))
    }
  }

  private def groupStage(report: SummaryReport) = {

    val countField = Seq(BsonDocument("$count" -> ReportField.Count.fieldName))

    val casesField = if (report.includeCases) Seq(BsonDocument("$push" -> BsonDocument("cases" -> "$ROOT"))) else Seq.empty

    val maxFields =
      report.maxFields.toList.map {
        case DaysSinceField(fieldName, underlyingField) =>
          BsonDocument(fieldName -> BsonDocument("$max" -> daysSince(s"$$$underlyingField")))
        case field => max(field.fieldName, BsonString(field.underlyingField))
      }

    val groupFields = countField ++ maxFields ++ casesField

    val groupBy = BsonDocument(report.groupBy.map {
      case ChapterField(fieldName, underlyingField) =>
        fieldName -> (substrBytes(s"$$$underlyingField", 0, 2))
      case DaysSinceField(fieldName, underlyingField) =>
        fieldName -> (daysSince(s"$$$underlyingField"))
      case StatusField(fieldName, _) =>
        fieldName -> (pseudoStatus())
      case CoalesceField(fieldName, fieldChoices) =>
        fieldName -> (coalesce(fieldChoices))
      case field =>
        field.fieldName -> (JsString(s"$$${field.underlyingField}"))
    }.toSeq)

    group(groupBy)(groupFields: _*)
  }

  private def getFieldValue(field: ReportField[_], json: Option[BsonDocument]): ReportResultField[_] = field match {
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

  private def getNumberFieldValue(field: ReportField[Long], json: Option[BsonDocument]): NumberResultField = field match {
    case field @ DaysSinceField(_, _) => field.withValue(json.flatMap(_.asOpt[Long]))
    case field @ NumberField(_, _)    => field.withValue(json.flatMap(_.asOpt[Long]))
  }

  override def summaryReport(report: SummaryReport, pagination: Pagination): Future[Paged[ResultGroup]] = {
    logger.info(s"Running report: $report with pagination $pagination")

    val countField = "resultCount"

    val runCount = collection
      .aggregateWith[BsonDocument](allowDiskUse = true) { framework =>


        val first = matchStage(framework, report)

        val rest = List(
          groupStage(framework, report),
          Count(countField)
        )

        (first, rest)

      }
      .headOption

    val runAggregation = collection
      .aggregateWith[BsonDocument](allowDiskUse = true) { framework =>


        val first = matchStage(framework, report)

        val rest = List(
          sortStage(framework, ReportField.Reference, SortDirection.ASCENDING),
          groupStage(framework, report),
          AddFields(BsonDocument("groupKey" -> "$_id")),
          Project(BsonDocument("_id"        -> 0)),
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
      .aggregateWith[BsonDocument](allowDiskUse = true) { framework =>


        val first = matchStage(framework, report)

        val rest = List(Count(countField))

        (first, rest)

      }
      .headOption

    val runAggregation = collection

        val fields = report.fields.toList.map {
            case ChapterField(fieldName, underlyingField) =>
              Field(fieldName, (substrBytes(s"$$$underlyingField", 0, 2)))
            case DaysSinceField(fieldName, underlyingField) =>
              Field(fieldName, (daysSince(s"$$$underlyingField")))
            case StatusField(fieldName, _) =>
              Field(fieldName, (pseudoStatus()))
            case CoalesceField(fieldName, fieldChoices) =>
              Field(fieldName, (coalesce(fieldChoices)))
            case field =>
              Field(field.fieldName, (s"$$${field.underlyingField}"))
          }

    val f = Aggregates.addFields()

        val rest = List(
          sortStage(report.sortBy, report.sortOrder),
          f,
          project(BsonDocument("_id" -> 0)),
          skip((pagination.page - 1) * pagination.pageSize),
          limit(pagination.pageSize)
        )

        (first, rest)

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

  private def queueGroupStage(report: QueueReport) = {
    BsonDocument(
      "$aggregate" -> BsonDocument(
        "$group" ->
          BsonArray.fromIterable(Seq(
            BsonDocument(ReportField.Team.fieldName -> ReportField.Team.underlyingField),
            BsonDocument(ReportField.CaseType.fieldName -> ReportField.CaseType.underlyingField)
        )),
        "$sum" -> BsonString(ReportField.Count.fieldName)
      )
    )
//    GroupMulti(
//      BsonDocument(ReportField.Team.fieldName -> ReportField.Team.underlyingField),
//      BsonDocument(ReportField.CaseType.fieldName -> ReportField.CaseType.underlyingField)
//    )(BsonDocument("$sum" -> BsonString(ReportField.Count.fieldName)))
  }

  private def queueSortStage(report: QueueReport) = {

    val sortDirection = (field: String) =>
      BsonDocument("$sort" -> BsonDocument(field -> (if (report.sortOrder == SortDirection.ASCENDING) 1 else -1)))

    def teamThenCaseType =
      Seq(sortDirection(s"_id.${ReportField.Team.fieldName}"), sortDirection(s"_id.${ReportField.CaseType.fieldName}"))

    def caseTypeThenTeam =
      Seq(sortDirection(s"_id.${ReportField.CaseType.fieldName}"), sortDirection(s"_id.${ReportField.Team.fieldName}"))

    def countThenTeamThenCaseType =
      sortDirection(ReportField.Count.fieldName) +: teamThenCaseType

    // Ideally we want to sort by both parts of the grouping key to improve sort stability
    report.sortBy match {
      case ReportField.Count    => countThenTeamThenCaseType
      case ReportField.CaseType => caseTypeThenTeam
      case ReportField.Team     => teamThenCaseType
    }
  }

  override def queueReport(
                            report: QueueReport,
                            pagination: Pagination
                          ): Future[Paged[QueueResultGroup]] = {
    logger.info(s"Running report: $report with pagination $pagination")

    val countField = "resultCount"

    val runCount = collection
      .aggregate()[BsonDocument](allowDiskUse = true) { framework =>

        val first = matchStage(framework, report)

        val rest = List(
          queueGroupStage(framework, report),
          count(countField)
        )

        (first, rest)

      }
      .headOption

    val runAggregation = collection
      .aggregateWith[BsonDocument](allowDiskUse = true) { framework =>


        val first = matchStage(report)

        val rest = List(
          queueGroupStage(report),
          queueSortStage(report),
          skip((pagination.page - 1) * pagination.pageSize),
          limit(pagination.pageSize)
        )

        (first, rest)

      }
      .fold[Seq[QueueResultGroup]](Seq.empty, pagination.pageSize) {
        case (rows, json) =>
          rows ++ Seq(
            QueueResultGroup(
              count    = json(ReportField.Count.fieldName).as[Int],
              team     = json("_id").as[BsonDocument].value.get(ReportField.Team.fieldName).flatMap(_.asOpt[String]),
              caseType = json("_id").as[BsonDocument].apply(ReportField.CaseType.fieldName).as[ApplicationType.Value]
            )
          )
      }

    (runCount, runAggregation).mapN {
      case (count, results) =>
        Paged(results, pagination, count.map(_(countField).as[Int]).getOrElse(0))
    }
  }
}
