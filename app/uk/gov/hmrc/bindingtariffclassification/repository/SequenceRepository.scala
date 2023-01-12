/*
 * Copyright 2023 HM Revenue & Customs
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
import org.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model._
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters._
import uk.gov.hmrc.bindingtariffclassification.model.Sequence
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

@ImplementedBy(classOf[SequenceMongoRepository])
trait SequenceRepository {

  def insert(e: Sequence): Future[Sequence]

  def getByName(name: String): Future[Sequence]

  def incrementAndGetByName(name: String): Future[Sequence]

  def deleteSequenceByName(name: String): Future[Unit]
}

@Singleton
class SequenceMongoRepository @Inject() (mongoComponent: MongoComponent)
    extends PlayMongoRepository[Sequence](
      collectionName = "sequences",
      mongoComponent = mongoComponent,
      domainFormat   = formatSequence,
      indexes = Seq(
        IndexModel(ascending("name"), IndexOptions().unique(true).name("name_Index"))
      )
    )
    with SequenceRepository
    with BaseMongoOperations[Sequence] {

  override def getByName(name: String): Future[Sequence] =
    collection.find(byName(name)).headOption().flatMap(valueOrStartSequence(name))

  override def incrementAndGetByName(name: String): Future[Sequence] =
    collection
      .findOneAndUpdate(
        filter  = byName(name),
        update  = Updates.inc("value", 1),
        options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
      )
      .headOption()
      .flatMap(valueOrStartSequence(name))

  override def insert(e: Sequence): Future[Sequence] = createOne(e)

  override def deleteSequenceByName(name: String): Future[Unit] =
    collection.deleteOne(byName(name)).toFuture().map(_ => ())

  private def valueOrStartSequence(name: String): Option[Sequence] => Future[Sequence] = {
    case Some(s: Sequence) => successful(s)
    case _                 => insert(Sequence(name, 1))
  }

  private def byName(name: String): Bson = equal("name", name)

}
