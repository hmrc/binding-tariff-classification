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

import com.google.inject.ImplementedBy
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.mongo.ReactiveRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@ImplementedBy(classOf[TeamMongoRepository])
trait TeamRepository {

  def insert(team: Team): Future[Team]

  def update(team: Team, upsert: Boolean): Future[Option[Team]]

  def getById(id: String): Future[Option[Team]]

}

@Singleton
class TeamMongoRepository @Inject()(mongoDbProvider: MongoDbProvider)
  extends ReactiveRepository[Team, BSONObjectID](
    collectionName = "teams",
    mongo = mongoDbProvider.mongo,
    domainFormat = MongoFormatters.formatTeam
  )
    with TeamRepository
    with MongoCrudHelper[Team] {

  override protected val mongoCollection: JSONCollection = collection

  override def indexes = Seq(
    createSingleFieldAscendingIndex(indexFieldKey = "id", isUnique = true),
    createSingleFieldAscendingIndex(indexFieldKey = "name", isUnique = false),
    createSingleFieldAscendingIndex(
      indexFieldKey = "managers",
      isUnique = false
    ),
  )

  private val defaultSortBy = Json.obj("timestamp" -> -1)

  override def getById(id: String): Future[Option[Team]] = {
    getOne(byId(id))
  }

  override def insert(team: Team): Future[Team] = createOne(team)

  override def update(team: Team, upsert: Boolean): Future[Option[Team]] = {
    updateDocument(selector = byId(team.id), update = team, upsert = upsert)
  }

  private def byId(id: String): JsObject =
    Json.obj("id" -> id)

  private def in[T](set: Set[T])(implicit fmt: Format[T]): JsValue =
    Json.obj("$in" -> JsArray(set.map(elm => Json.toJson(elm)).toSeq))

  private def mappingNoneOrSome: String => JsValue = {
    case "none" => Json.obj("$eq" -> JsArray.empty)
    case "some" => Json.obj("$gt" -> JsArray.empty)
    case v => in(Set(v))
  }

}

