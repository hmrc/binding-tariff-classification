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
import org.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{empty, equal}
import org.mongodb.scala.model.Indexes._
import org.mongodb.scala.model.{IndexModel, IndexOptions, ReplaceOptions}
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters._
import uk.gov.hmrc.bindingtariffclassification.model.{Keyword, Paged, Pagination}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[KeywordsMongoRepository])
trait KeywordsRepository {

  def insert(keyword: Keyword): Future[Keyword]

  def update(keyword: Keyword, upsert: Boolean): Future[Option[Keyword]]

  def delete(name: String): Future[Unit]

  def findAll(pagination: Pagination): Future[Paged[Keyword]]

}

@Singleton
class KeywordsMongoRepository @Inject() (mongoComponent: MongoComponent)
    extends PlayMongoRepository[Keyword](
      collectionName = "keywords",
      mongoComponent = mongoComponent,
      domainFormat   = formatKeywords,
      indexes = Seq(
        IndexModel(ascending("name"), IndexOptions().unique(true).name("name_Index"))
      )
    )
    with KeywordsRepository
    with BaseMongoOperations[Keyword] {

  override def insert(keyword: Keyword): Future[Keyword] = createOne(keyword)

  override def update(keyword: Keyword, upsert: Boolean): Future[Option[Keyword]] =
    collection
      .replaceOne(filter = byName(keyword.name), replacement = keyword, ReplaceOptions().upsert(upsert))
      .toFuture()
      .flatMap(_ => collection.find(byName(keyword.name)).first().toFutureOption())

  override def findAll(pagination: Pagination): Future[Paged[Keyword]] =
    countMany(
      empty(),
      defaultSortBy,
      pagination
    )

  private def byName(name: String): Bson = equal("name", name)

  override def delete(name: String): Future[Unit] =
    collection.deleteOne(equal("name", name)).map(_ => ()).head()

}
