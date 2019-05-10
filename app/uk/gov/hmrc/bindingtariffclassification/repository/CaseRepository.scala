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

package uk.gov.hmrc.bindingtariffclassification.repository

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json.toJson
import play.api.libs.json._
import reactivemongo.api.indexes.Index
import reactivemongo.bson.{BSONArray, BSONDocument, BSONDouble, BSONObjectID, BSONString}
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.bindingtariffclassification.crypto.Crypto
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.{NEW, OPEN}
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatCase
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.reporting.{CaseReport, CaseReportField, CaseReportGroup, ReportResult}
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CaseRepository {

  def insert(c: Case): Future[Case]

  def update(c: Case, upsert: Boolean): Future[Option[Case]]

  def incrementDaysElapsed(increment: Double): Future[Int]

  def getByReference(reference: String): Future[Option[Case]]

  def get(search: CaseSearch, pagination: Pagination): Future[Paged[Case]]

  def deleteAll(): Future[Unit]

  def generateReport(report: CaseReport): Future[Seq[ReportResult]]
}

@Singleton
class EncryptedCaseMongoRepository @Inject()(repository: CaseMongoRepository, crypto: Crypto) extends CaseRepository {

  private def encrypt: Case => Case = crypto.encrypt

  private def decrypt: Case => Case = crypto.decrypt

  override def insert(c: Case): Future[Case] = repository.insert(encrypt(c)).map(decrypt)

  override def update(c: Case, upsert: Boolean): Future[Option[Case]] = repository.update(encrypt(c), upsert).map(_.map(decrypt))

  override def incrementDaysElapsed(increment: Double): Future[Int] = repository.incrementDaysElapsed(increment)

  override def getByReference(reference: String): Future[Option[Case]] = repository.getByReference(reference).map(_.map(decrypt))

  override def get(search: CaseSearch, pagination: Pagination): Future[Paged[Case]] = {
    repository.get(enryptSearch(search), pagination).map(_.map(decrypt))
  }

  override def deleteAll(): Future[Unit] = repository.deleteAll()

  private def enryptSearch(search: CaseSearch) = {
    val eoriEnc: Option[String] = search.filter.eori.map(crypto.encryptString)
    search.copy(filter = search.filter.copy(eori = eoriEnc))
  }

  override def generateReport(report: CaseReport): Future[Seq[ReportResult]] = repository.generateReport(report)
}

@Singleton
class CaseMongoRepository @Inject()(mongoDbProvider: MongoDbProvider, mapper: SearchMapper)
  extends ReactiveRepository[Case, BSONObjectID](
    collectionName = "cases",
    mongo = mongoDbProvider.mongo,
    domainFormat = MongoFormatters.formatCase) with CaseRepository with MongoCrudHelper[Case] {

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

  override def indexes: Seq[Index] = {
    // TODO: We need to add relevant indexes for each possible search
    // TODO: We should add compound indexes for searches involving multiple fields
    uniqueSingleFieldIndexes.map(createSingleFieldAscendingIndex(_, isUnique = true)) ++
      nonUniqueSingleFieldIndexes.map(createSingleFieldAscendingIndex(_, isUnique = false))
  }

  override def insert(c: Case): Future[Case] = {
    createOne(c)
  }

  override def update(c: Case, upsert: Boolean): Future[Option[Case]] = {
    updateDocument(
      selector = mapper.reference(c.reference),
      update = c,
      upsert = upsert
    )
  }

  override def getByReference(reference: String): Future[Option[Case]] = {
    getOne(selector = mapper.reference(reference))
  }

  override def get(search: CaseSearch, pagination: Pagination): Future[Paged[Case]] = {
    getMany(
      filterBy = mapper.filterBy(search.filter),
      sortBy = search.sort.map(mapper.sortBy).getOrElse(Json.obj()),
      pagination
    )
  }

  override def deleteAll(): Future[Unit] = {
    removeAll().map(_ => ())
  }

  override def incrementDaysElapsed(increment: Double = 1): Future[Int] = {
    val statuses = List(OPEN, NEW)
    collection.update(
      selector = BSONDocument(
        "status" -> BSONDocument(
          "$in" -> BSONArray(statuses.map(s => BSONString(s.toString)))
        )
      ),
      update = BSONDocument(
        "$inc" -> BSONDocument("daysElapsed" -> BSONDouble(increment))
      ),
      multi = true
    ).map(_.nModified)
  }

  override def generateReport(report: CaseReport): Future[Seq[ReportResult]] = {
    import MongoFormatters.formatInstant
    import collection.BatchCommands.AggregationFramework._

    val groupField = report.group match {
      case CaseReportGroup.QUEUE => "queueId"
    }

    val reportField = report.field match {
      case CaseReportField.ACTIVE_DAYS_ELAPSED => "daysElapsed"
      case CaseReportField.REFERRED_DAYS_ELAPSED => "referredDaysElapsed"
    }

    val group: PipelineOperator = GroupField(groupField)("field" -> PushField(reportField))

    val filters = Seq[JsObject]()
      .++(report.filter.decisionStartDate.map { range =>
        Json.obj("decision.effectiveStartDate" -> Json.obj(
          "$lte" -> toJson(range.max),
          "$gte" -> toJson(range.min)
        ))
      })
      .++(report.filter.status.map { statuses =>
        Json.obj("status" -> Json.obj(
          "$in" -> JsArray(statuses.map(_.toString).map(JsString).toSeq)
        ))
      })
      .++(report.filter.assigneeId.map {
        case "none" =>  Json.obj("assignee.id" -> JsNull)
        case a =>   Json.obj("assignee.id" -> a)
      })
      .++(report.filter.reference.map { references =>
        Json.obj("reference" -> Json.obj("$in" -> JsArray(references.map(JsString).toSeq)))
      })

    val aggregation = filters match {
      case s if s.isEmpty =>
        (group, List.empty)
      case s if s.size == 1 =>
        (Match(s.head), List(group))
      case s =>
        (Match(Json.obj("$and" -> JsArray(s))), List(group))
    }

    collection.aggregateWith[JsObject]()(_ => aggregation)
      .collect[List](Int.MaxValue, reactivemongo.api.Cursor.FailOnError())
      .map {
        _.map { obj: JsObject =>
          ReportResult(
            Option(obj.value("_id")).filter(_.isInstanceOf[JsString]).map(_.as[JsString].value),
            obj.value("field").as[JsArray].value.map(_.as[JsNumber].value.toInt).toList
          )
        }
      }
  }
}
