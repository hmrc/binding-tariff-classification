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

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import reactivemongo.api.indexes.Index
import reactivemongo.bson.{BSONArray, BSONDocument, BSONDouble, BSONObjectID, BSONString}
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.bindingtariffclassification.model.JsonFormatters.formatCase
import uk.gov.hmrc.bindingtariffclassification.model.search.CaseParamsFilter
import uk.gov.hmrc.bindingtariffclassification.model.sort.CaseSort
import uk.gov.hmrc.bindingtariffclassification.model.{Case, CaseStatus, JsonFormatters}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[CaseMongoRepository])
trait CaseRepository {

  def insert(c: Case): Future[Case]

  def update(c: Case): Future[Option[Case]]

  def incrementDaysElapsed(increment: Double): Future[Int]

  def getByReference(reference: String): Future[Option[Case]]

  def get(searchBy: CaseParamsFilter, sortedBy: Option[CaseSort]): Future[Seq[Case]]

  def deleteAll(): Future[Unit]
}

@Singleton
class CaseMongoRepository @Inject()(mongoDbProvider: MongoDbProvider, jsonMapper: JsonObjectMapper)
  extends ReactiveRepository[Case, BSONObjectID](
    collectionName = "cases",
    mongo = mongoDbProvider.mongo,
    domainFormat = JsonFormatters.formatCase,
    idFormat = ReactiveMongoFormats.objectIdFormats) with CaseRepository with MongoCrudHelper[Case] {

  override val mongoCollection: JSONCollection = collection

  lazy private val uniqueSingleFieldIndexes = Seq("reference")
  lazy private val nonUniqueSingleFieldIndexes = Seq(
    "queueId",
    "assigneeId",
    "status",
    "daysElapsed"
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

  override def update(c: Case): Future[Option[Case]] = {
    updateDocument(selector = jsonMapper.fromReference(c.reference), update = c)
  }

  override def getByReference(reference: String): Future[Option[Case]] = {
    getOne(jsonMapper.fromReference(reference))
  }

  override def get(searchBy: CaseParamsFilter, sortedBy: Option[CaseSort] = None): Future[Seq[Case]] = {

    val sorting = sortedBy match {
      case Some(sort: CaseSort) => Json.obj(sort.field.toString -> sort.direction.id)
      case None => Json.obj()
    }

    getMany(jsonMapper.from(searchBy), sorting)
  }

  override def deleteAll(): Future[Unit] = {
    removeAll().map(_ => ())
  }

  override def incrementDaysElapsed(increment: Double = 1): Future[Int] = {
    val statuses = List(CaseStatus.OPEN, CaseStatus.NEW)
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
}
