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

import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.changestream.ChangeStreamDocument
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
import uk.gov.hmrc.bindingtariffclassification.common.Logging
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CaseKeywordViewMaterializer @Inject() (
  mongoComponent: MongoComponent,
  appConfig: AppConfig,
  caseRepository: CaseMongoRepository
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[CaseKeywordViewRow](
      collectionName = "caseKeywordsRowView",
      mongoComponent = mongoComponent,
      domainFormat = CaseKeywordViewRow.format,
      indexes = Seq(
        IndexModel(
          Indexes.compoundIndex(
            Indexes.ascending("keyword"),
            Indexes.ascending("reference")
          ),
          IndexOptions().name("keyword_reference_view_idx")
        )
      ),
      replaceIndexes = appConfig.replaceIndexes
    )
    with Logging {

  private val batchSize = 10000

  def startListening(): Future[Unit] = {
    logger.info("Initializing and rebuilding Case Keywords View.")

    rebuildViewFromScratch()
      .flatMap { _ =>
        logger.info("View rebuild completed. Starting live Change Stream listener.")

        caseRepository.collection
          .watch()
          .subscribe(
            (change: ChangeStreamDocument[Case]) => handleStreamChange(change),
            (e: Throwable) =>
              if (e.getMessage.contains("replica sets")) {
                logger.warn(
                  "Local Standalone MongoDB detected. Change Stream (live updates) is disabled. Rebuild on startup will still work fine."
                )
              } else {
                logger.error("Error in Case Keyword View Change Stream", e)
              },
            () => logger.info("Case Keyword View Change Stream closed.")
          )
        Future.successful(())
      }
      .recover { case e: Throwable =>
        logger.error("Failed to initialize or listen to Change Stream", e)
      }
  }

  private def rebuildViewFromScratch(): Future[Unit] = {
    logger.info("Clearing view collection sequentially.")
    collection.deleteMany(Filters.empty()).toFuture().flatMap { _ =>
      caseRepository.collection.countDocuments().toFuture().flatMap { totalCases =>
        logger.info(s"Total cases to sync: $totalCases. Processing batches.")

        def processBatch(skipCount: Int): Future[Unit] =
          if (skipCount >= totalCases) {
            Future.successful(())
          } else {
            caseRepository.collection
              .find()
              .skip(skipCount)
              .limit(batchSize)
              .toFuture()
              .flatMap { batchCases =>
                val viewRows = batchCases.flatMap(transformCaseToRows)
                if (viewRows.nonEmpty) {
                  collection.insertMany(viewRows).toFuture().flatMap { _ =>
                    processBatch(skipCount + batchSize)
                  }
                } else {
                  processBatch(skipCount + batchSize)
                }
              }
          }
        processBatch(0)
      }
    }
  }

  private def handleStreamChange(change: ChangeStreamDocument[Case]): Future[Unit] = {
    val caseId = Option(change.getDocumentKey)
      .flatMap(k => Option(k.get("_id")))
      .map(_.asObjectId().getValue.toString)
      .getOrElse("")

    change.getOperationType.name() match {
      case "INSERT" | "UPDATE" | "REPLACE" =>
        Option(change.getFullDocument) match {
          case Some(updatedCase) =>
            syncSingleCase(updatedCase)
          case None if caseId.nonEmpty =>
            caseRepository.collection.find(equal("_id", caseId)).headOption().flatMap {
              case Some(c) => syncSingleCase(c)
              case None    => Future.successful(())
            }
          case _ => Future.successful(())
        }
      case "DELETE" if caseId.nonEmpty =>
        collection.deleteMany(equal("caseId", caseId)).toFuture().map(_ => ())
      case _ => Future.successful(())
    }
  }

  private def syncSingleCase(c: Case): Future[Unit] = {
    val caseId = c.reference
    collection.deleteMany(equal("caseId", caseId)).toFuture().flatMap { _ =>
      val rows = transformCaseToRows(c)
      if (rows.nonEmpty) collection.insertMany(rows).toFuture().map(_ => ())
      else Future.successful(())
    }
  }

  private def transformCaseToRows(c: Case): List[CaseKeywordViewRow] = {
    val (goodsName, liabilityStatus) = c.application match {
      case bti: BTIApplication =>
        (Option(bti.goodName), None)

      case liability: LiabilityOrder =>
        (liability.goodName, Some(liability.status.toString))

      case correspondence: CorrespondenceApplication =>
        (None, None)

      case misc: MiscApplication =>
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
        caseType = Option(c.application.`type`.toString),
        daysElapsed = c.daysElapsed.toInt,
        liabilityStatus = liabilityStatus
      )
    }
  }

  def findRows(filter: Bson, skip: Int, limit: Int): Future[Seq[CaseKeywordViewRow]] =
    collection.find(filter).skip(skip).limit(limit).toFuture()

  def countRows(filter: Bson): Future[Long] =
    collection.countDocuments(filter).toFuture()
}
