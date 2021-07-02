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
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Sorts.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import play.api.Logging
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.bindingtariffclassification.model.{JobRunEvent, MongoFormatters}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class MigrationLockRepository @Inject() (mongoComponent: MongoComponent)
  extends PlayMongoRepository[JobRunEvent](
    collectionName = "migrations",
    mongoComponent          = mongoComponent,
    domainFormat   = MongoFormatters.formatJobRunEvent,
    indexes = Seq(
      IndexModel(ascending("name"),IndexOptions().unique(true))
    )
  )
    with MongoCrudHelper[JobRunEvent] with Logging {

  override protected val mongoCollection: MongoCollection[JobRunEvent] = collection

  private val mongoDuplicateKeyErrorCode: Int = 11000

  def lock(e: JobRunEvent): Future[Boolean] =
    insertOne(e) map { _ =>
      logger.debug(s"Took Lock for [${e.name}]")
      true
    } recover {
      case error: DatabaseException if error.isNotAPrimaryError =>
        // Do not allow the migration job to proceed due to errors on secondary nodes, and attempt to rollback the changes
        logger.error(s"Lock failed on secondary node", error)
        rollback(e)
        false
      case error: DatabaseException if error.code.contains(mongoDuplicateKeyErrorCode) =>
        logger.debug(s"Lock already exists for [${e.name}]", error)
        false
      case error: Exception =>
        logger.error(s"Unable to take Lock for [${e.name}]", error)
        false
    }

  def rollback(e: JobRunEvent): Future[Unit] =
    deleteOne(equal("name", e.name)).map { _ =>
      logger.debug(s"Removed Lock for [${e.name}]")
      ()
    }
}
