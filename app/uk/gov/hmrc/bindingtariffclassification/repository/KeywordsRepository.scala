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
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Sorts.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}

import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import reactivemongo.bson.{BSONObjectID, BSONString}
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters._
import uk.gov.hmrc.bindingtariffclassification.model.{Keyword, MongoFormatters, Paged, Pagination}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Projections._
import org.mongodb.scala.model.Sorts._

@Singleton
class KeywordsRepository @Inject() (mongoComponent: MongoComponent)
  extends PlayMongoRepository[Keyword](
    collectionName = "keywords",
    mongoComponent = mongoComponent,
    domainFormat = MongoFormatters.formatKeywords,
    indexes = Seq(
      IndexModel(ascending("name"),IndexOptions().unique(true))
    )
  ) with MongoCrudHelper[Keyword] {

  override protected val mongoCollection: MongoCollection[Keyword] = collection

  def insertKeyword(keyword: Keyword): Future[Keyword] = insertOne(keyword)

  def updateKeyword(keyword: Keyword, upsert: Boolean): Future[Option[Keyword]] =
    updateOne(equal("name", keyword.name), BsonDocument(Json.toJson(keyword).toString()), upsert)

  def findKeywords(pagination: Pagination): Future[Paged[Keyword]] = findMany(pagination = pagination)

  def deleteKeywords(keyword: String): Future[Unit] = deleteMany(equal("name", keyword))
}
