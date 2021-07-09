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
import org.mongodb.scala.bson.{BsonArray, BsonDateTime, BsonDocument, BsonString}
import org.mongodb.scala.model.Sorts._
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import play.api.libs.json._
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class EventRepository @Inject() (mongoComponent: MongoComponent)
    extends PlayMongoRepository[Event](
      collectionName = "events",
      mongoComponent = mongoComponent,
      domainFormat = MongoFormatters.formatEvent,
      indexes = Seq(
        IndexModel(ascending("id"),IndexOptions().unique(true)),
        IndexModel(ascending("caseReference"),IndexOptions().unique(false)),
        IndexModel(ascending("type"),IndexOptions().unique(false))
      )
    ) with MongoCrudHelper[Event] {

  override protected val mongoCollection: MongoCollection[Event] = collection

  private val defaultSortBy = BsonDocument("timestamp" -> -1)

  def findEvents(search: EventSearch, pagination: Pagination): Future[Paged[Event]] =
    findMany(selector(search), defaultSortBy, pagination)

  def deleteEvents(search: EventSearch): Future[Unit] = deleteMany(selector(search))

  private def selector(search: EventSearch): BsonDocument = {
    val queries = Seq[BsonDocument]()
      .++(search.caseReference.map(r => BsonDocument("caseReference" -> in(r))))
      .++(search.`type`.map(t => BsonDocument("details.type" -> in(t))))
      .++(search.timestampMin.map(t => BsonDocument("timestamp" -> BsonDocument("$gte" -> BsonDateTime(t.toEpochMilli)))))
      .++(search.timestampMax.map(t => BsonDocument("timestamp" -> BsonDocument("$lte" -> BsonDateTime(t.toEpochMilli)))))

    queries match {
      case Nil           => BsonDocument()
      case single :: Nil => single
      case many          => BsonDocument("$and" -> BsonArray.fromIterable(many))
    }
  }

  private def in[T](set: Set[T])(implicit fmt: Format[T]): BsonDocument =
    BsonDocument("$in" -> BsonArray.fromIterable(set.map(elm => BsonString(Json.toJson(elm).toString()))))
}
