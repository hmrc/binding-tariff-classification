/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.bson.{BsonDocument, BsonNull, BsonValue}
import org.mongodb.scala.model.Accumulators.{max, sum}
import org.mongodb.scala.model.Aggregates._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Indexes.{ascending => asc, descending => desc}
import org.mongodb.scala.model.Sorts.{ascending, descending, orderBy}
import org.mongodb.scala.model._
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.crypto.Crypto
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.reporting._
import uk.gov.hmrc.bindingtariffclassification.repository.BaseMongoOperations.pagedResults
import uk.gov.hmrc.bindingtariffclassification.sort.SortDirection
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs.toBson
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.Instant
import javax.inject.{Inject, Singleton}
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

  override def getAllByEori(eori: String): Future[List[Case]] =
    repository.getAllByEori(crypto.encryptString.apply(eori)).map(_.map(decrypt))

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
  mongoComponent: MongoComponent,
  mapper: SearchMapper,
  updateMapper: UpdateMapper
) extends PlayMongoRepository[Case](
      collectionName = "cases",
      mongoComponent = mongoComponent,
      domainFormat   = MongoFormatters.formatCase,
      // TODO: We need to add relevant indexes for each possible search
      // TODO: We should add compound indexes for searches involving multiple fields
      indexes = Seq(
        IndexModel(asc("reference"), IndexOptions().unique(true).name("reference_Index")),
        IndexModel(asc("assignee.id"), IndexOptions().unique(false).name("assignee.id_Index")),
        IndexModel(asc("queueId"), IndexOptions().unique(false).name("queueId_Index")),
        IndexModel(asc("status"), IndexOptions().unique(false).name("status_Index")),
        IndexModel(desc("createdDate"), IndexOptions().unique(false).name("createdDate_Index")),
        IndexModel(
          asc("application.holder.eori"),
          IndexOptions().unique(false).name("application.holder.eori_Index")
        ),
        IndexModel(
          asc("application.agent.eoriDetails.eori"),
          IndexOptions().unique(false).name("application.agent.eoriDetails.eori_Index")
        ),
        IndexModel(
          asc("application.type"),
          IndexOptions().unique(false).name("application.type_Index")
        ),
        IndexModel(
          asc("decision.effectiveEndDate"),
          IndexOptions().unique(false).name("decision.effectiveEndDate_Index")
        ),
        IndexModel(
          asc("decision.bindingCommodityCode"),
          IndexOptions().unique(false).name("decision.bindingCommodityCode_Index")
        ),
        IndexModel(asc("daysElapsed"), IndexOptions().unique(false).name("daysElapsed_Index")),
        IndexModel(asc("keywords"), IndexOptions().unique(false).name("keywords_Index"))
      )
    )
    with CaseRepository
    with BaseMongoOperations[Case] {

  protected[this] val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def insert(c: Case): Future[Case] = createOne(c)

  override def update(c: Case, upsert: Boolean): Future[Option[Case]] =
    collection
      .replaceOne(filter = mapper.reference(c.reference), replacement = c, ReplaceOptions().upsert(upsert))
      .toFuture()
      .flatMap(_ => collection.find(mapper.reference(c.reference)).first().toFutureOption())

  override def update(reference: String, caseUpdate: CaseUpdate): Future[Option[Case]] =
    collection
      .findOneAndUpdate(
        filter  = mapper.reference(reference),
        update  = updateMapper.updateCase(caseUpdate),
        options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
      )
      .toFutureOption()

  override def getByReference(reference: String): Future[Option[Case]] =
    collection.find(mapper.reference(reference)).limit(1).headOption()

  override def get(search: CaseSearch, pagination: Pagination): Future[Paged[Case]] =
    countMany(
      toBson(mapper.filterBy(search.filter)).asDocument(),
      search.sort.map(cs => toBson(mapper.sortBy(cs)).asDocument()).getOrElse(defaultSortBy),
      pagination
    )

  override def getAllByEori(eori: String): Future[List[Case]] =
    collection.find(equal("application.holder.eori", eori)).toFuture().map(seq => seq.toList)

  override def deleteAll(): Future[Unit] =
    collection.deleteMany(empty()).toFuture().map(_ => ())

  override def delete(reference: String): Future[Unit] =
    collection.deleteOne(equal("reference", reference)).toFuture.map(_ => ())

  private def greaterThan(json: JsValueWrapper): JsObject =
    Json.obj("$gte" -> json)

  private def trunc(json: JsValueWrapper): JsObject =
    Json.obj("$trunc" -> json)

  private def divide(dividend: JsValueWrapper, divisor: JsValueWrapper): JsObject =
    Json.obj("$divide" -> Json.arr(dividend, divisor))

  private def subtract(minuend: JsValueWrapper, subtrahend: JsValueWrapper): JsObject =
    Json.obj("$subtract" -> Json.arr(minuend, subtrahend))

  private def substrBytes(operand: JsValueWrapper, offset: JsValueWrapper, length: JsValueWrapper): JsObject =
    Json.obj("$substrBytes" -> Json.arr(operand, offset, length))

  private def andQ(operands: JsValueWrapper*): JsObject =
    Json.obj("$and" -> Json.arr(operands: _*))

  private def eqQ(lExpr: JsValueWrapper, rExpr: JsValueWrapper): JsObject =
    Json.obj("$eq" -> Json.arr(lExpr, rExpr))

  private def inQ(expr: JsValueWrapper, arrayExpr: JsValueWrapper): JsObject =
    Json.obj("$in" -> Json.arr(expr, arrayExpr))

  private def notEmpty(operand: JsValueWrapper): JsObject =
    Json.obj("$gt" -> Json.arr(Json.obj("$size" -> operand), 0))

  private def filter(input: JsValueWrapper, cond: JsValueWrapper): JsObject =
    Json.obj("$filter" -> Json.obj("input" -> input, "cond" -> cond))

  private def daysSince(operand: JsValueWrapper): JsValue = {
    val milliSecondsInOneDay = 86400000
    trunc(
      divide(
        subtract(Json.toJson(appConfig.clock.instant()), operand),
        milliSecondsInOneDay
      )
    )
  }

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

    val isAppeal = andQ(
      eqQ(s"$$${ReportField.Status.underlyingField}", CaseStatus.COMPLETED.toString),
      notNull("$decision.appeal"),
      notEmpty(
        filter(
          input = "$decision.appeal",
          cond  = inQ("$$this.type", AppealType.appealTypes.map(Json.toJson(_)))
        )
      )
    )

    val isReview = andQ(
      eqQ(s"$$${ReportField.Status.underlyingField}", CaseStatus.COMPLETED.toString),
      notNull("$decision.appeal"),
      notEmpty(
        filter(
          input = "$decision.appeal",
          cond  = inQ("$$this.type", AppealType.reviewTypes.map(Json.toJson(_)))
        )
      )
    )

    val isExpired = andQ(
      eqQ(s"$$${ReportField.Status.underlyingField}", CaseStatus.COMPLETED.toString),
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
      case (field, expr) =>
        Json.obj("$ifNull" -> Json.arr("$" + field, expr))
    }

  private def matchStage(report: Report): Bson = {

    val GatewayTeamId = "1"

    val caseTypeFilter =
      if (report.caseTypes.isEmpty) {
        empty()
      } else {
        in(ReportField.CaseType.underlyingField, report.caseTypes.map(_.toString).toSeq: _*)
      }

    val statusFilter =
      if (report.statuses.isEmpty) {
        empty()
      } else {
        val (concreteStatuses, pseudoStatuses) = report.statuses.partition(p => CaseStatus.fromPseudoStatus(p).nonEmpty)

        val concreteFilter =
          in(ReportField.Status.underlyingField, concreteStatuses.map(_.toString).toSeq: _*)

        val pseudoFilters =
          pseudoStatuses.collect {
            case PseudoCaseStatus.EXPIRED =>
              and(
                equal(ReportField.Status.underlyingField, PseudoCaseStatus.COMPLETED.toString),
                lte(ReportField.DateExpired.underlyingField, appConfig.clock.instant()),
                size("decision.appeal", 0)
              )
            case PseudoCaseStatus.UNDER_APPEAL =>
              and(
                equal(ReportField.Status.underlyingField, PseudoCaseStatus.COMPLETED.toString),
                elemMatch("decision.appeal", in("type", AppealType.appealTypes.map(_.toString).toSeq: _*))
              )
            case PseudoCaseStatus.UNDER_REVIEW =>
              and(
                equal(ReportField.Status.underlyingField, PseudoCaseStatus.COMPLETED.toString),
                and(
                  not(elemMatch("decision.appeal", in("type", AppealType.appealTypes.map(_.toString).toSeq: _*))),
                  elemMatch("decision.appeal", in("type", AppealType.reviewTypes.map(_.toString).toSeq: _*))
                )
              )
          }
        or((Seq(concreteFilter) ++ pseudoFilters.toSeq): _*)
      }

    val liabilityStatusesFilter =
      if (report.liabilityStatuses.isEmpty) {
        empty()
      } else {
        in(ReportField.LiabilityStatus.underlyingField, report.liabilityStatuses.map(_.toString).toSeq: _*)
      }

    val teamFilter =
      if (report.teams.isEmpty) {
        empty()
      } else {
        in(
          ReportField.Team.underlyingField,
          report.teams
            .map(teamId => if (teamId == GatewayTeamId) null else teamId)
            .toSeq: _*
        )
      }

    val minDateFilter = if (report.dateRange.min != Instant.MIN) {
      gte(ReportField.DateCreated.underlyingField, report.dateRange.min)
    } else {
      empty()
    }

    val maxDateFilter = if (report.dateRange.max != Instant.MAX) {
      lte(ReportField.DateCreated.underlyingField, report.dateRange.max)
    } else {
      empty()
    }

    val dateFilter =
      if (report.dateRange == InstantRange.allTime) {
        empty()
      } else {
        and(minDateFilter, maxDateFilter)
      }

    val assigneeFilter = report match {
      case _: CaseReport =>
        empty()
      case _: SummaryReport =>
        empty()
      case queue: QueueReport =>
        queue.assignee.map(assignee => equal(ReportField.User.underlyingField, assignee)).getOrElse {
          equal(ReportField.User.underlyingField, null)
        }
    }

    val bson = and(caseTypeFilter, statusFilter, teamFilter, dateFilter, assigneeFilter, liabilityStatusesFilter)
    `match`(bson)
  }

  private def summarySortStage(report: SummaryReport) = {
    val sortField = report match {
      case summary: SummaryReport if summary.groupBy.toSeq.contains(report.sortBy) =>
        s"groupKey.${report.sortBy.fieldName}"
      case _ =>
        report.sortBy.fieldName
    }

    (report.sortOrder: @unchecked) match {
      case SortDirection.ASCENDING =>
        sort(ascending(sortField))
      case SortDirection.DESCENDING =>
        sort(descending(sortField))
    }
  }

  private def sortStage(
    sortBy: ReportField[_],
    sortOrder: SortDirection.Value
  ) =
    // If not sorting by reference, add it as a secondary sort field to ensure stable sorting
    ((sortOrder, sortBy): @unchecked) match {
      case (SortDirection.ASCENDING, ReportField.Reference) =>
        sort(ascending(sortBy.underlyingField))
      case (SortDirection.DESCENDING, ReportField.Reference) =>
        sort(descending(sortBy.underlyingField))
      case (SortDirection.ASCENDING, _) =>
        sort(
          orderBy(ascending(sortBy.underlyingField), ascending(ReportField.Reference.underlyingField))
        )
      case (SortDirection.DESCENDING, _) =>
        sort(
          orderBy(descending(sortBy.underlyingField), descending(ReportField.Reference.underlyingField))
        )
    }

  private def groupStage(report: SummaryReport) = {

    val casesField = if (report.includeCases) Seq(Accumulators.push("cases", "$$ROOT")) else Seq.empty

    val maxFields =
      report.maxFields.toList.map {
        case DaysSinceField(fieldName, underlyingField) =>
          Accumulators.max(
            fieldName,
            toBson(daysSince(s"$$$underlyingField"))
          )
        case field =>
          max(field.fieldName, s"$$${field.underlyingField}")
      }

    val countField = sum(ReportField.Count.fieldName, 1)

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

    group(toBson(groupBy), (Seq(countField) ++ maxFields ++ casesField): _*)
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

    val rest = Seq(groupStage(report), count(countField))

    val futureCount = collection
      .aggregate[BsonDocument](
        Seq(matchStage(report)) ++ rest
      )
      .allowDiskUse(true)
      .headOption()

    val runAggregation = collection
      .aggregate[BsonDocument](
        Seq(matchStage(report)) ++
          Seq(
            sortStage(ReportField.Reference, SortDirection.ASCENDING),
            groupStage(report),
            addFields(Field("groupKey", "$_id")),
            project(equal("_id", 0)),
            summarySortStage(report),
            skip((pagination.page - 1) * pagination.pageSize),
            limit(pagination.pageSize)
          )
      )
      .allowDiskUse(true)
      .toFuture()
      .map(bsonDocSeq =>
        bsonDocSeq
          .map(bsonDocument => Json.parse(bsonDocument.toJson).as[JsObject])
          .map { json =>
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
          }
      )

    pagedResults(futureCount, runAggregation, pagination)
  }

  override def caseReport(
    report: CaseReport,
    pagination: Pagination
  ): Future[Paged[Map[String, ReportResultField[_]]]] = {
    logger.info(s"Running report: $report with pagination $pagination")

    val futureCount = collection
      .aggregate[BsonDocument] {
        Seq(matchStage(report)) :+ count(countField)
      }
      .allowDiskUse(true)
      .headOption()

    val fields =
      report.fields.toList.map {
        case ChapterField(fieldName, underlyingField) =>
          Field(fieldName, toBson(substrBytes(s"$$$underlyingField", 0, 2)))
        case DaysSinceField(fieldName, underlyingField) =>
          Field(fieldName, toBson(daysSince(s"$$$underlyingField")))
        case StatusField(fieldName, _) =>
          Field(fieldName, toBson(pseudoStatus()))
        case CoalesceField(fieldName, fieldChoices) =>
          Field(fieldName, toBson(coalesce(fieldChoices)))
        case field =>
          Field(field.fieldName, toBson(s"$$${field.underlyingField}"))
      }

    val runAggregation = collection
      .aggregate[BsonDocument](
        Seq(matchStage(report)) ++
          Seq(
            sortStage(report.sortBy, report.sortOrder),
            addFields(fields: _*),
            project(equal("_id", 0)),
            skip((pagination.page - 1) * pagination.pageSize),
            limit(pagination.pageSize)
          )
      )
      .allowDiskUse(true)
      .toFuture()
      .map(bsonDocSeq =>
        bsonDocSeq
          .map(bsonDocument => Json.parse(bsonDocument.toJson).as[JsObject])
          .map { json =>
            report.fields.toSeq
              .map(field => field.fieldName -> getFieldValue(field, json.value.get(field.fieldName)))
              .toMap[String, ReportResultField[_]]
          }
      )

    pagedResults(futureCount, runAggregation, pagination)
  }

  private def queueGroupStage = {
    val fields = Json.obj(
      ReportField.Team.fieldName     -> s"$$${ReportField.Team.underlyingField}",
      ReportField.CaseType.fieldName -> s"$$${ReportField.CaseType.underlyingField}"
    )
    group(toBson(fields), Accumulators.sum(ReportField.Count.fieldName, 1))
  }

  private def queueSortStage(report: QueueReport) = {

    val sortDirection = (field: String) =>
      if (report.sortOrder == SortDirection.ASCENDING) ascending(field) else descending(field)

    def teamThenCaseType =
      Seq(sortDirection(s"_id.${ReportField.Team.fieldName}"), sortDirection(s"_id.${ReportField.CaseType.fieldName}"))

    def caseTypeThenTeam =
      Seq(sortDirection(s"_id.${ReportField.CaseType.fieldName}"), sortDirection(s"_id.${ReportField.Team.fieldName}"))

    def countThenTeamThenCaseType =
      sortDirection(ReportField.Count.fieldName) +: teamThenCaseType

    // Ideally we want to sort by both parts of the grouping key to improve sort stability
    (report.sortBy: @unchecked) match {
      case ReportField.Count    => sort(orderBy(countThenTeamThenCaseType: _*))
      case ReportField.CaseType => sort(orderBy(caseTypeThenTeam: _*))
      case ReportField.Team     => sort(orderBy(teamThenCaseType: _*))
    }
  }

  override def queueReport(
    report: QueueReport,
    pagination: Pagination
  ): Future[Paged[QueueResultGroup]] = {
    logger.info(s"Running report: $report with pagination $pagination")

    val rest = Seq(queueGroupStage, count(countField))
    val futureCount = collection
      .aggregate[BsonDocument] {
        Seq(matchStage(report)) ++ rest
      }
      .allowDiskUse(true)
      .headOption()

    val runAggregation = collection
      .aggregate[BsonDocument](
        Seq(matchStage(report)) ++
          Seq(
            queueGroupStage,
            queueSortStage(report),
            skip((pagination.page - 1) * pagination.pageSize),
            limit(pagination.pageSize)
          )
      )
      .allowDiskUse(true)
      .toFuture()
      .map(bsonDocSeq =>
        bsonDocSeq
          .map { bsonDocument =>
            def wrapAsOptionOfString(field: BsonValue): Option[String] =
              if (field.isNull) None else Some(field.asString().getValue)
            QueueResultGroup(
              count = bsonDocument.getInt32(ReportField.Count.fieldName).getValue,
              team  = wrapAsOptionOfString(bsonDocument.getDocument("_id").get(ReportField.Team.fieldName, BsonNull())),
              caseType = ApplicationType
                .withName(bsonDocument.getDocument("_id").getString(ReportField.CaseType.fieldName).getValue)
            )
          }
      )

    pagedResults(futureCount, runAggregation, pagination)
  }
}
