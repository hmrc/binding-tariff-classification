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
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model._
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[UsersMongoRepository])
trait UsersRepository {

  def insert(user: Operator): Future[Operator]

  def update(user: Operator, upsert: Boolean): Future[Option[Operator]]

  def getById(id: String): Future[Option[Operator]]

  def search(search: UserSearch, pagination: Pagination): Future[Paged[Operator]]
}

@Singleton
class UsersMongoRepository @Inject() (mongoComponent: MongoComponent)
    extends PlayMongoRepository[Operator](
      collectionName = "users",
      mongoComponent = mongoComponent,
      domainFormat   = formatOperator,
      indexes = Seq(
        IndexModel(ascending("id"), IndexOptions().unique(true).name("id_Index")),
        IndexModel(ascending("role"), IndexOptions().unique(false).name("role_Index")),
        IndexModel(ascending("memberOfTeams"), IndexOptions().unique(false).name("memberOfTeams_Index"))
      )
    )
    with UsersRepository
    with BaseMongoOperations[Operator] {

  override def getById(id: String): Future[Option[Operator]] =
    collection.find(byId(id)).limit(1).headOption()

  override def search(search: UserSearch, pagination: Pagination): Future[Paged[Operator]] =
    countMany(
      selector(search),
      defaultSortBy,
      pagination
    )

  override def insert(user: Operator): Future[Operator] = collection.insertOne(user).toFuture().map(_ => user)

  override def update(user: Operator, upsert: Boolean): Future[Option[Operator]] =
    collection
      .replaceOne(filter = byId(user.id), replacement = user, ReplaceOptions().upsert(upsert))
      .toFuture()
      .flatMap(_ => collection.find(byId(user.id)).first().toFutureOption())

  private def byId(id: String): Bson = equal("id", id)

  private def selector(search: UserSearch): Bson = {
    val notDeletedFilter = equal("deleted", false)

    val optionalRoleFilter = search.role.map(r => in("role", r.map(_.toString).toSeq: _*))
    val optionalTeamFilter = search.team.map(t => mappingNoneOrSome("memberOfTeams", t))
    val filters            = List(Some(notDeletedFilter), optionalRoleFilter, optionalTeamFilter).flatten
    Filters.and(filters: _*)
  }

  private def mappingNoneOrSome(field: String, value: String): Bson = value match {
    case "none" => equal(field, size(field, 0))
    case "some" => gt(field, size(field, 0))
    case v      => in(field, Seq(v): _*)
  }
}
