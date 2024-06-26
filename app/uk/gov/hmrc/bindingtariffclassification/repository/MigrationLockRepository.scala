/*
 * Copyright 2024 HM Revenue & Customs
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
import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.model.Filters.{empty, equal}
import org.mongodb.scala.model.Indexes._
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import play.api.i18n.Lang.logger
import uk.gov.hmrc.bindingtariffclassification.model.{JobRunEvent, MongoFormatters}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[MigrationLockMongoRepository])
trait MigrationLockRepository {

  def lock(e: JobRunEvent): Future[Boolean]

  def delete(e: JobRunEvent): Future[Unit]

  def deleteAll(): Future[Unit]

  def findOne(name: String): Future[Option[JobRunEvent]]

}

@Singleton
class MigrationLockMongoRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[JobRunEvent](
      collectionName = "migrations",
      mongoComponent = mongoComponent,
      domainFormat = MongoFormatters.formatJobRunEvent,
      indexes = Seq(
        IndexModel(ascending("name"), IndexOptions().unique(true).name("name_Index"))
      )
    )
    with MigrationLockRepository {

  private val mongoDuplicateKeyErrorCode: Int = 11000

  override def lock(e: JobRunEvent): Future[Boolean] =
    collection.insertOne(e).toFuture().map { _ =>
      logger.debug(s"Took Lock for [${e.name}]")
      true
    } recover {
      case error: MongoWriteException if isNotAPrimaryError(error.getCode) =>
        // Do not allow the migration job to proceed due to errors on secondary nodes, and attempt to rollback the changes
        logger.error(s"Lock failed on secondary node", error)
        delete(e)
        false
      case error: MongoWriteException if error.getCode == mongoDuplicateKeyErrorCode =>
        logger.error(s"Lock already exists for [${e.name}]", error)
        false
      case NonFatal(error) =>
        logger.error(s"Unable to take Lock for [${e.name}]", error)
        false
    }

  override def delete(e: JobRunEvent): Future[Unit] =
    collection.deleteOne(equal("name", e.name)).toFuture().flatMap { _ =>
      logger.debug(s"Removed Lock for [${e.name}]")
      Future.unit
    }

  override def deleteAll(): Future[Unit] =
    collection.deleteMany(empty()).toFuture().flatMap(_ => Future.unit)

  override def findOne(name: String): Future[Option[JobRunEvent]] =
    collection.find(equal("name", name)).headOption()

  /** Tells if this error is due to a write on a secondary node. */
  private def isNotAPrimaryError(code: Int): Boolean = Some(code).exists {
    case 10058 | 10107 | 13435 | 13436 => true
    case _                             => false
  }
}
