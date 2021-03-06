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

import play.api.libs.json._
import reactivemongo.api.{Cursor, QueryOpts, ReadConcern}
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.bindingtariffclassification.model.{Paged, Pagination}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MongoCrudHelper[T] extends MongoIndexCreator {

  protected val mongoCollection: JSONCollection

  protected def getOne(selector: JsObject)(implicit r: OFormat[T]): Future[Option[T]] =
    mongoCollection.find[JsObject, T](selector).one[T]

  protected def getMany(filterBy: JsObject, sortBy: JsObject, pagination: Pagination = Pagination())(
    implicit r: OFormat[T]
  ): Future[Paged[T]] =
    for {
      results <- mongoCollection
                  .find[JsObject, T](filterBy)
                  .sort(sortBy)
                  .options(
                    QueryOpts(skipN = (pagination.page - 1) * pagination.pageSize, batchSizeN = pagination.pageSize)
                  )
                  .cursor[T]()
                  .collect[List](pagination.pageSize, Cursor.FailOnError[List[T]]())
      count <- mongoCollection
                .count(Some(filterBy), limit = Some(0), skip = 0, hint = None, readConcern = ReadConcern.Local)
    } yield Paged(results, pagination.page, pagination.pageSize, count.toInt)

  protected def createOne(document: T)(implicit w: OWrites[T]): Future[T] =
    mongoCollection.insert(document).map(_ => document)

  protected def updateDocument(selector: JsObject, update: T, fetchNew: Boolean = true, upsert: Boolean = false)(
    implicit returnFormat: OFormat[T]
  ): Future[Option[T]] =
    updateInternal(selector, Json.toJson(update).as[JsObject], fetchNew, upsert)

  protected def update(selector: JsObject, update: JsObject, fetchNew: Boolean, upsert: Boolean = false)(
    implicit returnFormat: OFormat[T]
  ): Future[Option[T]] =
    updateInternal(selector, update, fetchNew, upsert)

  private def updateInternal(selector: JsObject, update: JsObject, fetchNew: Boolean, upsert: Boolean)(
    implicit returnFormat: OFormat[T]
  ): Future[Option[T]] =
    mongoCollection
      .findAndUpdate(
        selector       = selector,
        update         = update,
        fetchNewObject = fetchNew,
        upsert         = upsert
      )
      .map(_.value.map(_.as[T]))

}
