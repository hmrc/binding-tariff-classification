/*
 * Copyright 2022 HM Revenue & Customs
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
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, empty}
import org.mongodb.scala.model._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[EventMongoRepository])
trait EventRepository {

  def insert(e: Event): Future[Event]

  def search(search: EventSearch, pagination: Pagination): Future[Paged[Event]]

  def deleteAll(): Future[Unit]

  def delete(search: EventSearch): Future[Unit]
}

@Singleton
class EventMongoRepository @Inject() (mongoComponent: MongoComponent)
    extends PlayMongoRepository[Event](
      collectionName = "events",
      mongoComponent = mongoComponent,
      domainFormat   = MongoFormatters.formatEvent,
      indexes = Seq(
        IndexModel(Indexes.ascending("id"), IndexOptions().unique(true).name("id_Index")),
        IndexModel(Indexes.ascending("caseReference"), IndexOptions().unique(false).name("caseReference_Index")),
        IndexModel(Indexes.ascending("type"), IndexOptions().unique(false).name("type_Index")),
        IndexModel(Indexes.descending("timestamp"), IndexOptions().unique(false).name("timestamp_Index"))
      )
    )
    with EventRepository
    with BaseMongoOperations[Event] {

  override def insert(e: Event): Future[Event] = createOne(e)

  override def search(search: EventSearch, pagination: Pagination): Future[Paged[Event]] =
    countMany(
      selector(search),
      Sorts.orderBy(Sorts.descending("timestamp")),
      pagination
    )

  override def deleteAll(): Future[Unit] =
    collection.deleteMany(empty()).toFuture().map(_ => Future.unit)

  override def delete(search: EventSearch): Future[Unit] =
    collection.deleteMany(filter = selector(search)).toFuture().map(_ => Future.unit)

  private def selector(search: EventSearch): Bson = {
    val queries = Seq[Bson]()
      .++(search.caseReference.map(r => Filters.in("caseReference", r.toList: _*)))
      .++(search.`type`.map(t => Filters.in("details.type", t.map(_.toString).toList: _*)))
      .++(search.timestampMin.map(t => Filters.gte("timestamp", t)))
      .++(search.timestampMax.map(t => Filters.lte("timestamp", t)))

    queries match {
      case Nil           => empty()
      case single :: Nil => single
      case many          => and(many: _*)
    }
  }

}
