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
import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatSequence
import uk.gov.hmrc.bindingtariffclassification.model.{MongoFormatters, Sequence}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[SequenceMongoRepository])
trait SequenceRepository {

  def insert(e: Sequence): Future[Sequence]

  def getByName(name: String): Future[Sequence]

  def incrementAndGetByName(name: String): Future[Sequence]
}

@Singleton
class SequenceMongoRepository @Inject()(mongoDbProvider: MongoDbProvider)
  extends ReactiveRepository[Sequence, BSONObjectID](
    collectionName = "sequences",
    mongo = mongoDbProvider.mongo,
    domainFormat = MongoFormatters.formatSequence,
    idFormat = ReactiveMongoFormats.objectIdFormats) with SequenceRepository with MongoCrudHelper[Sequence] {

  override val mongoCollection: JSONCollection = collection

  override def indexes = Seq(
    createSingleFieldAscendingIndex(indexFieldKey = "name", isUnique = true)
  )

  override def getByName(name: String): Future[Sequence] = {
    getOne(byName(name)).flatMap(valueOrStartSequence(name))
  }

  override def incrementAndGetByName(name: String): Future[Sequence] = {
    update(
      selector = byName(name),
      update = Json.obj("$inc" -> Json.obj("value" -> 1)),
      fetchNew = true
    ).flatMap(valueOrStartSequence(name))
  }

  override def insert(e: Sequence): Future[Sequence] = {
    createOne(e)
  }

  private def valueOrStartSequence(name: String): Option[Sequence] => Future[Sequence] = {
    case Some(s: Sequence) => Future.successful(s)
    case _ => insert(Sequence(name, 1))
  }

  private def byName(name: String) = {
    Json.obj("name" -> name)
  }

}
