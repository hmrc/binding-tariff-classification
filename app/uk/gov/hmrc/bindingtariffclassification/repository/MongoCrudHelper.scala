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

import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{CountOptions, FindOneAndUpdateOptions}
import uk.gov.hmrc.bindingtariffclassification.model.{Paged, Pagination}
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Projections._
import org.mongodb.scala.model.Sorts._
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag

trait MongoCrudHelper[T] extends MongoIndexCreator {

  val mongoCollection: MongoCollection[T]

  def findOne(selector: Bson)(implicit c: ClassTag[T]): Future[Option[T]] =
    mongoCollection.find(selector).headOption()

  def findMany(filterBy: Bson = BsonDocument(), sortBy: Bson = BsonDocument(),
               pagination: Pagination = Pagination())(implicit c: ClassTag[T]): Future[Paged[T]] =
    for {
      results <- mongoCollection
        .find(filterBy)
        .sort(sortBy)
        .skip((pagination.page - 1) * pagination.pageSize)
        .batchSize(pagination.pageSize)
        .toFuture()
      count <- mongoCollection
        .countDocuments(filterBy,CountOptions().skip(0).hint(BsonDocument())).toFuture()
    } yield Paged(results, pagination.page, pagination.pageSize, count.toInt)

  def insertOne(document: T): Future[T] =
    mongoCollection.insertOne(document).toFuture().map(_ => document)

  def insertMany(document: Seq[T]): Future[Seq[T]] =
    mongoCollection.insertMany(document).toFuture().map(_ => document)

  def updateOne(selector: Bson, update: BsonDocument, upsert: Boolean = false): Future[Option[T]] =
    mongoCollection.findOneAndUpdate(selector,update, FindOneAndUpdateOptions().upsert(upsert)).toFutureOption()

  def updateOne(selector: Bson, update: T, upsert: Boolean = false)(implicit format: OFormat[T]): Future[Option[T]] =
    mongoCollection.findOneAndUpdate(selector,BsonDocument(Json.toJson(update).toString()),
      FindOneAndUpdateOptions().upsert(upsert)).toFutureOption()

  def deleteOne(selector: Bson): Future[Unit] =
    mongoCollection.deleteOne(selector).toFuture().map { _ => ()}

  def deleteMany(selector: Bson): Future[Unit] =
    mongoCollection.deleteMany(selector).toFuture().map { _ => ()}

  def deleteAll: Future[Unit] =
    mongoCollection.deleteMany(BsonDocument()).toFuture().map { _ => ()}
}
