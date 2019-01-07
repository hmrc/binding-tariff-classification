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
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatEvent
import uk.gov.hmrc.bindingtariffclassification.model.{Event, MongoFormatters}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[EventMongoRepository])
trait EventRepository {

  def insert(e: Event): Future[Event]

  def getById(id: String): Future[Option[Event]]

  def getByCaseReference(caseReference: String): Future[Seq[Event]]

  def deleteAll(): Future[Unit]
}

@Singleton
class EventMongoRepository @Inject()(mongoDbProvider: MongoDbProvider)
  extends ReactiveRepository[Event, BSONObjectID](
    collectionName = "events",
    mongo = mongoDbProvider.mongo,
    domainFormat = MongoFormatters.formatEvent,
    idFormat = ReactiveMongoFormats.objectIdFormats) with EventRepository with MongoCrudHelper[Event] {

  override val mongoCollection: JSONCollection = collection

  override def indexes = Seq(
    // TODO: We need to create an index (composed by a single or multiple fields) considering all possible searches needed by the UI.
    createSingleFieldAscendingIndex(indexFieldKey = "id", isUnique = true),
    createSingleFieldAscendingIndex(indexFieldKey = "caseReference", isUnique = false)
  )

  override def insert(e: Event): Future[Event] = {
    createOne(e)
  }

  private def byId(id: String): JsObject = {
    Json.obj("id" -> id)
  }

  private def byCaseReference(caseReference: String): JsObject = {
    Json.obj("caseReference" -> caseReference)
  }

  override def getById(id: String): Future[Option[Event]] = {
    getOne(byId(id))
  }

  override def getByCaseReference(caseReference: String): Future[Seq[Event]] = {
    getMany(byCaseReference(caseReference), Json.obj())
  }

  override def deleteAll(): Future[Unit] = {
    removeAll().map(_ => ())
  }

}
