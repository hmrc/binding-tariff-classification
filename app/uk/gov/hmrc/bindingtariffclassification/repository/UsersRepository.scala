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
import org.mongodb.scala.bson.{BsonArray, BsonDocument, BsonString}
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Sorts.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import play.api.libs.json._
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UsersRepository @Inject()(mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[Operator](
      collectionName = "users",
      mongoComponent = mongoComponent,
      domainFormat = MongoFormatters.formatOperator,
      indexes = Seq(
        IndexModel(ascending("id"),IndexOptions().unique(true)),
        IndexModel(ascending("role"),IndexOptions().unique(false)),
        IndexModel(ascending("memberOfTeams"),IndexOptions().unique(false))
      )
    ) with MongoCrudHelper[Operator] {

  override protected val mongoCollection: MongoCollection[Operator] = collection

  def findOperator(id: String): Future[Option[Operator]] = {
    findOne(equal("id", id))
  }

  def findOperators(search: UserSearch,
                      pagination: Pagination): Future[Paged[Operator]] = {
    findMany(filterBy = selector(search),pagination = pagination)
  }

  def updateOperator(user: Operator,
                      upsert: Boolean): Future[Option[Operator]] = {
    updateOne(equal("id", user.id), user, upsert)
  }

  private def selector(search: UserSearch): BsonDocument = {
    val queries = Seq[BsonDocument](BsonDocument("deleted" -> false))
      .++(search.role.map(r => BsonDocument("role" -> in(r))))
      .++(search.team.map(t => BsonDocument("memberOfTeams" -> mappingNoneOrSome(t))))
    queries match {
      case Nil           => BsonDocument()
      case single :: Nil => single
      case many          => BsonDocument("$and" -> BsonArray.fromIterable(many))
    }
  }

  private def in[T](set: Set[T])(implicit fmt: Format[T]): BsonDocument =
    BsonDocument("$in" -> BsonArray.fromIterable(set.map(elm => BsonString(Json.toJson(elm).toString())).toSeq))

  private def mappingNoneOrSome: String => BsonDocument = {
    case "none" => BsonDocument("$eq" -> BsonArray())
    case "some" => BsonDocument("$gt" -> BsonArray())
    case v      => in(Set(v))
  }
}
