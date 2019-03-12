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
import play.api.libs.json.Json
import reactivemongo.api.indexes.Index
import reactivemongo.bson.{BSONArray, BSONDocument, BSONDouble, BSONObjectID, BSONString}
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.bindingtariffclassification.crypto.Crypto
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.{NEW, OPEN}
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatCase
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait CaseRepository {

  def insert(c: Case): Future[Case]

  def update(c: Case, upsert: Boolean): Future[Option[Case]]

  def incrementDaysElapsed(increment: Double): Future[Int]

  def getByReference(reference: String): Future[Option[Case]]

  def get(search: Search, pagination: Pagination): Future[Paged[Case]]

  def deleteAll(): Future[Unit]
}

@Singleton
class EncryptedCaseMongoRepository @Inject()(repository: CaseMongoRepository, crypto: Crypto) extends CaseRepository {

  private def encrypt: Case => Case = crypto.encrypt
  private def decrypt: Case => Case = crypto.decrypt

  override def insert(c: Case): Future[Case] = repository.insert(encrypt(c)).map(decrypt)

  override def update(c: Case, upsert: Boolean): Future[Option[Case]] = repository.update(encrypt(c), upsert).map(_.map(decrypt))

  override def incrementDaysElapsed(increment: Double): Future[Int] = repository.incrementDaysElapsed(increment)

  override def getByReference(reference: String): Future[Option[Case]] = repository.getByReference(reference).map(_.map(decrypt))

  override def get(search: Search, pagination: Pagination): Future[Paged[Case]] = {
    repository.get(enryptSearch(search), pagination).map(_.map(decrypt))
  }

  override def deleteAll(): Future[Unit] = repository.deleteAll()

  private def enryptSearch(search: Search) = {
    val eoriEnc: Option[String] = search.filter.eori.map(crypto.encryptString)
    search.copy(filter = search.filter.copy(eori = eoriEnc))
  }
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

  override def get(search: Search, pagination: Pagination): Future[Paged[Case]] = {
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

}
