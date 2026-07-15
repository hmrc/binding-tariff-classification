/*
 * Copyright 2026 HM Revenue & Customs
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

import org.bson.{BsonDocument, BsonObjectId}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.{and, equal}
import org.mongodb.scala.model.changestream.ChangeStreamDocument
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes, ReplaceOptions}
import uk.gov.hmrc.bindingtariffclassification.common.Logging
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.ZonedDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CaseKeywordViewMaterializer @Inject() (
  mongoComponent: MongoComponent,
  appConfig: AppConfig,
  caseRepository: CaseMongoRepository,
  migrationLockRepository: MigrationLockRepository
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[CaseKeywordViewRow](
      collectionName = "caseKeywordsRowView",
      mongoComponent = mongoComponent,
      domainFormat = CaseKeywordViewRow.format,
      indexes = Seq(
        IndexModel(
          Indexes.compoundIndex(
            Indexes.ascending("keyword"),
            Indexes.ascending("caseId")
          ),
          IndexOptions()
            .name("keyword_caseId_view_idx")
            .unique(true)
        ),
        IndexModel(
          Indexes.ascending("caseId"),
          IndexOptions()
            .name("caseId_idx")
        )
      ),
      replaceIndexes = appConfig.replaceIndexes
    )
    with Logging {

  private val rebuildLock =
    JobRunEvent(
      name = "case-keyword-view-rebuild",
      runDate = ZonedDateTime.now()
    )
  private val batchSize = 5000

  def startListening(): Future[Unit] = {
    logger.info("Initializing and rebuilding Case Keywords View.")

    migrationLockRepository.findOne(rebuildLock.name).flatMap {
      case Some(_) =>
        logger.info(
          "Another instance is already rebuilding Case Keywords View. Starting Change Stream only."
        )
        startChangeStream()

      case None =>
        migrationLockRepository.lock(rebuildLock).flatMap {
          case true =>
            logger.info("Acquired Case Keywords View rebuild lock.")

            rebuildWithRetry()
              .flatMap { _ =>
                logger.info("View rebuild completed. Starting Change Stream.")
                startChangeStream()
              }
              .recoverWith { case e =>
                logger.error("Failed to rebuild Case Keywords View after retry.", e)

                migrationLockRepository
                  .delete(rebuildLock)
                  .transformWith(_ => Future.failed(e))
              }
              .andThen { case _ =>
                migrationLockRepository.delete(rebuildLock)
              }

          case false =>
            logger.info(
              "Failed to acquire rebuild lock. Another instance is rebuilding. Starting Change Stream only."
            )
            startChangeStream()
        }
    }
  }

  private def rebuildWithRetry(retriesLeft: Int = 3): Future[Unit] =
    rebuildViewFromScratch().recoverWith {
      case e if retriesLeft > 0 =>
        logger.warn(s"Case Keyword View rebuild failed. Retrying once. Cause: ${e.getMessage}", e)
        rebuildWithRetry(retriesLeft - 1)
    }

  private def startChangeStream(): Future[Unit] = {
    caseRepository.collection
      .watch()
      .subscribe(
        (change: ChangeStreamDocument[Case]) => handleStreamChange(change),
        (e: Throwable) =>
          if (e.getMessage.contains("replica sets")) {
            logger.warn(
              "Local Standalone MongoDB detected. Change Stream disabled."
            )
          } else {
            logger.error("Error in Case Keyword View Change Stream", e)
          },
        () => logger.info("Case Keyword View Change Stream closed.")
      )

    Future.unit
  }

  private[repository] def rebuildViewFromScratch(): Future[Unit] = {
    logger.info("Clearing view collection sequentially.")
    for {
      deleteResult <- collection
                        .deleteMany(Filters.empty())
                        .toFuture()

      _ = logger.info(
            s"Deleted ${deleteResult.getDeletedCount} existing rows"
          )

      totalCases <- caseRepository.collection
                      .countDocuments()
                      .toFuture()

      _ = logger.info(
            s"Total cases to sync: $totalCases"
          )

      _ <- processBatches(totalCases)

    } yield ()
  }

  private def processBatches(totalCases: Long): Future[Unit] = {

    def processBatch(skipCount: Int): Future[Unit] =
      if (skipCount >= totalCases) {
        Future.unit
      } else {

        caseRepository.collection
          .find()
          .skip(skipCount)
          .limit(batchSize)
          .toFuture()
          .flatMap { batchCases =>

            val rows = batchCases.flatMap(transformCaseToRows)

            if (rows.nonEmpty) {
              collection
                .insertMany(rows)
                .toFuture()
                .flatMap { _ =>
                  logger.info(
                    s"Inserted ${rows.size} keyword rows. Progress: ${skipCount + batchCases.size}/$totalCases cases"
                  )

                  processBatch(skipCount + batchSize)
                }
            } else {
              processBatch(skipCount + batchSize)
            }
          }
      }

    processBatch(0)
  }

  private[repository] def handleStreamChange(
    change: ChangeStreamDocument[Case]
  ): Future[Unit] = {

    val caseId = extractCaseId(Option(change.getDocumentKey))

    resolveCase(change, caseId)
      .flatMap { caseOpt =>
        applyChange(
          change.getOperationType.name(),
          caseId,
          caseOpt
        )
      }
  }

  private[repository] def extractCaseId(documentKey: Option[BsonDocument]): Option[String] =
    documentKey.flatMap { k =>
      Option(k.get("_id")).map(_.asObjectId().getValue.toString)
    }

  private[repository] def resolveCase(
    change: ChangeStreamDocument[Case],
    caseId: Option[String]
  ): Future[Option[Case]] =
    Option(change.getFullDocument) match {
      case some @ Some(_) => Future.successful(some)

      case None =>
        caseId match {
          case Some(id) =>
            caseRepository.collection
              .find(equal("_id", id))
              .headOption()

          case None =>
            Future.successful(None)
        }
    }

  private[repository] def applyChange(
    op: String,
    caseId: Option[String],
    caseOpt: Option[Case]
  ): Future[Unit] =
    op match {
      case "DELETE" =>
        caseId match {
          case Some(id) =>
            collection
              .deleteMany(equal("caseId", id))
              .toFuture()
              .map(_ => ())

          case None =>
            Future.unit
        }

      case "INSERT" | "UPDATE" | "REPLACE" =>
        caseOpt match {
          case Some(c) =>
            syncSingleCase(c)

          case None =>
            Future.unit
        }

      case _ =>
        Future.unit
    }

  private[repository] def syncSingleCase(c: Case): Future[Unit] =

    collection
      .deleteMany(equal("caseId", c.reference))
      .toFuture()
      .flatMap { _ =>

        val rows = transformCaseToRows(c)

        if (rows.nonEmpty) {
          collection
            .insertMany(rows)
            .toFuture()
            .map(_ => ())
        } else {
          Future.unit
        }
      }

  private def transformCaseToRows(c: Case): List[CaseKeywordViewRow] = {

    val (goodsName, liabilityStatus) = c.application match {

      case bti: BTIApplication =>
        (Option(bti.goodName), None)

      case liability: LiabilityOrder =>
        (liability.goodName, Some(liability.status.toString))

      case _: CorrespondenceApplication =>
        (None, None)

      case _: MiscApplication =>
        (None, None)
    }

    c.keywords.toList.map { keyword =>
      CaseKeywordViewRow(
        keyword = keyword,
        caseId = c.reference,
        reference = c.reference,
        status = c.status.toString,
        assignee = c.assignee.map(_.name.getOrElse("")),
        team = c.queueId,
        goodsName = goodsName,
        caseType = Some(c.application.`type`.toString),
        daysElapsed = c.daysElapsed.toInt,
        liabilityStatus = liabilityStatus
      )
    }
  }

  def findRows(
    filter: Bson,
    skip: Int,
    limit: Int
  ): Future[Seq[CaseKeywordViewRow]] =
    collection
      .find(filter)
      .skip(skip)
      .limit(limit)
      .toFuture()

  def countRows(filter: Bson): Future[Long] =
    collection
      .countDocuments(filter)
      .toFuture()
}
