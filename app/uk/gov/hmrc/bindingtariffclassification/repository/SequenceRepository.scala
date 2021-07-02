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
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatSequence
import uk.gov.hmrc.bindingtariffclassification.model.{MongoFormatters, Sequence}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Projections._
import org.mongodb.scala.model.Sorts._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class SequenceRepository @Inject() (mongoComponent: MongoComponent)
    extends PlayMongoRepository[Sequence](
      collectionName = "sequences",
      mongoComponent = mongoComponent,
      domainFormat   = MongoFormatters.formatSequence,
      indexes = Seq(
        IndexModel(ascending("name"),IndexOptions().unique(true)),
      )
    ) with MongoCrudHelper[Sequence] {

  override protected val mongoCollection: MongoCollection[Sequence] = collection

  def findSequence(name: String): Future[Sequence] =
    findOne(equal("name", name)).flatMap(valueOrStartSequence(name))

  def findSequenceAndIncrement(name: String): Future[Sequence] = {
    updateOne(equal("name", name), BsonDocument("$inc" -> BsonDocument("value" -> 1)))
    .flatMap(valueOrStartSequence(name))
  }

  def deleteSequence(name: String): Future[Unit] =
    deleteOne(equal("name", name)).map(_ => ())

  private def valueOrStartSequence(name: String): Option[Sequence] => Future[Sequence] = {
    case Some(s: Sequence) => Future.successful(s)
    case _                 => insertOne(Sequence(name, 1))
  }
}
