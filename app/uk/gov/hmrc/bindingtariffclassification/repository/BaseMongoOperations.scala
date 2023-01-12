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

import cats.implicits.catsSyntaxTuple2Semigroupal
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Sorts
import uk.gov.hmrc.bindingtariffclassification.model.{Paged, Pagination}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

trait BaseMongoOperations[T] {

  protected val countField: String  = BaseMongoOperations.countField
  protected val _id                 = "_id"
  protected val defaultSortBy: Bson = Sorts.orderBy(Sorts.ascending(_id))

  protected val collection: MongoCollection[T]

  private def countDocument(filter: Bson): Future[Long] =
    collection.countDocuments(filter).toFuture()

  protected def countMany(filter: Bson, sort: Bson, pagination: Pagination)(
    implicit
    ec: ExecutionContext,
    ct: ClassTag[T]
  ): Future[Paged[T]] = {
    val count = countDocument(filter)
    val results = collection
      .find(filter)
      .sort(sort)
      .skip((pagination.page - 1) * pagination.pageSize)
      .limit(pagination.pageSize)
      .toFuture()

    (count, results).mapN {
      case (resultsSize, events) =>
        Paged(events, pagination, resultsSize)
    }
  }

  protected def createOne(item: T)(implicit ec: ExecutionContext): Future[T] =
    collection.insertOne(item).toFuture().map(_ => item)

}

object BaseMongoOperations {
  val countField = "resultCount"

  def pagedResults[A](
    futureCount: Future[Option[BsonDocument]],
    runAggregation: Future[Seq[A]],
    pagination: Pagination
  )(implicit ec: ExecutionContext): Future[Paged[A]] =
    (futureCount, runAggregation).mapN {
      case (count, results) =>
        val totalCount = count.map(field => field.getInt32(countField).getValue).getOrElse(0)
        Paged(results, pagination, totalCount.toLong)
    }
}
