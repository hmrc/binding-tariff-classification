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
import play.api.Logger
import play.api.libs.json.Json
import reactivemongo.api.indexes.Index
import reactivemongo.bson.{BSONArray, BSONDocument, BSONDouble, BSONObjectID, BSONString}
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.bindingtariffclassification.crypto.Crypto
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.{NEW, OPEN}
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatCase
import uk.gov.hmrc.bindingtariffclassification.model.search.Search
import uk.gov.hmrc.bindingtariffclassification.model.{Case, MongoFormatters}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait CaseRepository {

  def insert(c: Case): Future[Case]

  def update(c: Case, upsert: Boolean): Future[Option[Case]]

  def incrementDaysElapsed(increment: Double): Future[Int]

  def getByReference(reference: String): Future[Option[Case]]

  def get(search: Search): Future[Seq[Case]]

  def deleteAll(): Future[Unit]
}

@Singleton
class EncryptedCaseMongoRepository @Inject()(repository: CaseMongoRepository, crypto: Crypto) extends CaseRepository {
  private def encrypt: Case => Case = crypto.encrypt
  private def encrypt(search: Search): Search = crypto.encrypt(search)
  private def decrypt: Case => Case = crypto.decrypt

  override def insert(c: Case): Future[Case] = repository.insert(encrypt(c)).map(decrypt)

  override def update(c: Case, upsert: Boolean): Future[Option[Case]] = repository.update(encrypt(c), upsert).map(_.map(decrypt))

  override def incrementDaysElapsed(increment: Double): Future[Int] = repository.incrementDaysElapsed(increment)

  override def getByReference(reference: String): Future[Option[Case]] = repository.getByReference(reference).map(_.map(decrypt))

  override def get(search: Search): Future[Seq[Case]] = {
    repository.get(encrypt(search)).map(_.map(decrypt))
  }

  override def deleteAll(): Future[Unit] = repository.deleteAll()
}

@Singleton
class CaseMongoRepository @Inject()(mongoDbProvider: MongoDbProvider, mapper: SearchMapper)
  extends ReactiveRepository[Case, BSONObjectID](
    collectionName = "cases",
    mongo = mongoDbProvider.mongo,
    domainFormat = MongoFormatters.formatCase,
    idFormat = ReactiveMongoFormats.objectIdFormats) with CaseRepository with MongoCrudHelper[Case] {

  override val mongoCollection: JSONCollection = collection

  //////////////////////////////////////////////////////////////////////////////////////////
  // TODO: remove this block of code when the old index has been deleted in all environments
  private def deleteWrongIndex(): Unit = {
    val oldIndex = "assigneeId_Index"
    collection.indexesManager.drop(oldIndex) onComplete {
      case Success(n) => Logger.warn(s"Index $oldIndex has been deleted: $n")
      case Failure(e) => Logger.error(s"Error found while deleting index $oldIndex.", e)
    }
  }
  deleteWrongIndex()
  //////////////////////////////////////////////////////////////////////////////////////////

  lazy private val uniqueSingleFieldIndexes = Seq("reference")
  lazy private val nonUniqueSingleFieldIndexes = Seq(
    "assignee.id",
    "application.holder.businessName", // TODO: to be removed if later we decide to use non-exact match search
    "queueId",
    "status",
    "decision.effectiveEndDate",
    "daysElapsed"
  )

  // TODO: text indexes could be used for searching keywords, comm code,  or non-exact match search (keywords + good description)

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

  override def get(search: Search): Future[Seq[Case]] = {
    getMany(
      filterBy = mapper.filterBy(search.filter),
      sortBy = search.sort.map(mapper.sortBy).getOrElse(Json.obj())
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
}
