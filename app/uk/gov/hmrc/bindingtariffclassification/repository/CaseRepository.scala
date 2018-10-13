/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import com.google.inject.ImplementedBy
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.bindingtariffclassification.model.{Case, JsonFormatters}
import uk.gov.hmrc.bindingtariffclassification.model.JsonFormatters.formatCase
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future

@ImplementedBy(classOf[CaseMongoRepository])
trait CaseRepository {

  def insert(c: Case): Future[Case]
  def update(c: Case): Future[Option[Case]]
  def getByReference(reference: String): Future[Option[Case]]
  def getAll: Future[Seq[Case]]
}

@Singleton
class CaseMongoRepository @Inject()(mongoDbProvider: MongoDbProvider)
  extends ReactiveRepository[Case, BSONObjectID](
    collectionName = "cases",
    mongo = mongoDbProvider.mongo,
    domainFormat = JsonFormatters.formatCase,
    idFormat = ReactiveMongoFormats.objectIdFormats) with CaseRepository with MongoCrudHelper[Case] {

  override val mongoCollection: JSONCollection = collection

  override def indexes = Seq(
    // TODO: We need to create an index (composed by a single or multiple fields) considering all possible searches needed by the UI.
    createSingleFieldAscendingIndex("reference", isUnique = true)
  )

  override def insert(c: Case): Future[Case] = {
    createOne(c)
  }

  override def update(c: Case): Future[Option[Case]] = {
    atomicUpdate(selectorByReference(c.reference), c)
  }

  private def selectorByReference(reference: String): JsObject = {
    Json.obj("reference" -> reference)
  }

  override def getByReference(reference: String): Future[Option[Case]] = {
    getOne(selectorByReference(reference))
  }

  override def getAll: Future[Seq[Case]] = {
    getMany(Json.obj())
  }

}
