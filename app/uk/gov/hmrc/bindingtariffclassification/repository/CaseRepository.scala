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


import javax.inject.{Inject, Singleton}
import com.google.inject.ImplementedBy
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.bindingtariffclassification.model.{Case, IsInsert}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future

@ImplementedBy(classOf[CaseMongoRepository])
trait CaseRepository {

  def save(c: Case): Future[(Case, IsInsert)]
}

@Singleton
class CaseMongoRepository @Inject()(mongoDbProvider: MongoDbProvider)
  extends ReactiveRepository[Case, BSONObjectID]("cases", mongoDbProvider.mongo,
    MongoFormatters.caseMongoFormat, ReactiveMongoFormats.objectIdFormats)
    with CaseRepository
    with MongoCrudHelper[Case] {

  override val mongoCollection: JSONCollection = collection
  private implicit val format = MongoFormatters.caseMongoFormat

  override def indexes = Seq(
    createSingleFieldAscendingIndex("reference", Some("referenceIndex"), true)
  )

  override def save(c: Case): Future[(Case, IsInsert)] = {
    save(c, selector(c.reference))
  }

  private def selector(reference: String): JsObject = {
    Json.obj(
      "reference" -> reference
    )
  }
}
