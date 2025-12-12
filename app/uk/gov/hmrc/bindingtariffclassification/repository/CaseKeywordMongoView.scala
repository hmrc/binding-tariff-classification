/*
 * Copyright 2025 HM Revenue & Customs
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
import org.mongodb.scala.model.Filters.*
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import play.api.Logging
import play.api.libs.json.Json
import uk.gov.hmrc.bindingtariffclassification.model.{CaseKeywordRow, MongoCodecs, Paged, Pagination}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs.toBson

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}

@Singleton
class CaseKeywordMongoView @Inject() (
                                       mongoComponent: MongoComponent
                                     )(implicit ec: ExecutionContext) extends Logging {

  private[repository] val caseKeywordsViewName = "caseKeywords"
  private[repository] val collectionName = "cases"

  logger.info(s"Initializing CaseKeywordMongoView with viewName: $caseKeywordsViewName")

  private val viewInitialized: Future[Unit] = initView.map(_ => ())

  Await.result(viewInitialized, 30.seconds)

  logger.info("View initialization complete, ready to serve requests")

  private lazy val collection = mongoComponent.database
    .getCollection[CaseKeywordRow](caseKeywordsViewName)
    .withCodecRegistry(MongoCodecs.caseKeyword)

  /** Flat view pipeline - one document per keyword-case pair
   */
  private def pipeline: Seq[BsonDocument] = {
    logger.info("Building aggregation pipeline...")
    val pipelineJson = Json.arr(

      Json.obj(
        "$match" -> Json.obj(
          "keywords" -> Json.obj(
            "$exists" -> true,
            "$ne"     -> Json.arr()
          )
        )
      ),

      Json.obj(
        "$unwind" -> "$keywords"
      ),

      Json.obj(
        "$lookup" -> Json.obj(
          "from"         -> "keywords",
          "localField"   -> "keywords",
          "foreignField" -> "name",
          "as"           -> "keywordInfo"
        )
      ),
      Json.obj(
        "$match" -> Json.obj(
          "keywordInfo" -> Json.obj("$size" -> 0)
        )
      ),
      Json.obj(
        "$project" -> Json.obj(
          "_id"       -> 0,
          "keyword"   -> "$keywords",
          "reference" -> "$reference",
          "user" -> Json.obj(
            "$ifNull" -> Json.arr("$assignee.name", null)
          ),
          "goods" -> Json.obj(
            "$ifNull" -> Json.arr("$application.goodName", null)
          ),
          "caseType" -> "$application.type",
          "status"   -> "$status",
          "liabilityStatus" -> Json.obj(
            "$ifNull" -> Json.arr("$application.status", null)
          ),
          "createdDate" -> "$createdDate",
          "daysElapsed" -> Json.obj(
            "$dateDiff" -> Json.obj(
              "startDate" -> "$createdDate",
              "endDate"   -> "$$NOW",
              "unit"      -> "day"
            )
          ),
          "overdue" -> Json.obj(
            "$or" -> Json.arr(
              Json.obj(
                "$and" -> Json.arr(
                  Json.obj("$eq" -> Json.arr("$application.type", "LIABILITY_ORDER")),
                  Json.obj("$eq" -> Json.arr("$application.status", "LIVE")),
                  Json.obj(
                    "$gte" -> Json.arr(
                      Json.obj(
                        "$dateDiff" -> Json.obj(
                          "startDate" -> "$createdDate",
                          "endDate"   -> "$$NOW",
                          "unit"      -> "day"
                        )
                      ),
                      5
                    )
                  )
                )
              ),
              Json.obj(
                "$gte" -> Json.arr(
                  Json.obj(
                    "$dateDiff" -> Json.obj(
                      "startDate" -> "$createdDate",
                      "endDate"   -> "$$NOW",
                      "unit"      -> "day"
                    )
                  ),
                  30
                )
              )
            )
          ),
          "approved" -> Json.obj(
            "$literal" -> false
          )
        )
      ),
      Json.obj(
        "$sort" -> Json.obj(
          "keyword" -> 1
        )
      )
    )

    val result = pipelineJson.value.map(v => toBson(v).asDocument()).toSeq
    logger.info(s"Pipeline built with ${result.size} stages")
    result
  }

  private[repository] def createView(viewName: String, viewOn: String): Future[Unit] = {
    logger.info(s"Creating view '$viewName' on collection '$viewOn'...")
    mongoComponent.database
      .createView(viewName, viewOn, pipeline)
      .toFuture()
      .map { _ =>
        logger.info(s"View '$viewName' created successfully")
      }
      .recover {
        case ex: Exception =>
          logger.error(s"Failed to create view '$viewName': ${ex.getMessage}", ex)
          throw ex
      }
  }

  private[repository] def dropView(viewName: String): Future[Unit] = {
    logger.info(s"Dropping view '$viewName'...")
    mongoComponent.database
      .getCollection(viewName)
      .drop()
      .toFuture()
      .map { _ =>
        logger.info(s"View '$viewName' dropped successfully")
      }
      .recover {
        case ex: Exception =>
          logger.warn(s"Failed to drop view '$viewName' (may not exist): ${ex.getMessage}")
          ()
      }
  }

  private[repository] def getView(viewName: String): MongoCollection[CaseKeywordRow] = {
    logger.info(s"Getting view collection '$viewName'...")
    mongoComponent.database
      .getCollection[CaseKeywordRow](viewName)
  }

  private[repository] def initView: Future[MongoCollection[CaseKeywordRow]] = {
    logger.info("Starting view initialization...")

    mongoComponent.database
      .listCollectionNames()
      .toFuture()
      .flatMap { collections =>
        logger.info(s"Existing collections: ${collections.take(10).mkString(", ")}${if (collections.size > 10) "..." else ""}")

        if (collections.contains(caseKeywordsViewName)) {
          logger.info(s"View '$caseKeywordsViewName' already exists, dropping it first...")
          dropView(caseKeywordsViewName)
        } else {
          logger.info(s"View '$caseKeywordsViewName' does not exist, creating new one...")
          Future.successful(())
        }
      }
      .flatMap { _ =>
        logger.info("Creating new view...")
        createView(caseKeywordsViewName, collectionName)
      }
      .flatMap { _ =>
        logger.info("Retrieving view collection and counting documents...")
        val viewCollection = getView(caseKeywordsViewName)

        viewCollection.countDocuments().toFuture().map { count =>
          logger.info(s"View created successfully! Total documents in view: $count")
          viewCollection
        }
      }
      .recoverWith {
        case ex: Exception =>
          logger.error(s"CRITICAL ERROR initializing view: ${ex.getMessage}", ex)
          Future.failed(ex)
      }
  }

  def fetchKeywordsFromCases(
                              pagination: Pagination,
                              approvedFilter: Option[Boolean] = None
                            ): Future[Paged[CaseKeywordRow]] = {
    logger.info(s"Fetching keywords with pagination: page=${pagination.page}, pageSize=${pagination.pageSize}, approved=$approvedFilter")

    val filter = approvedFilter match {
      case Some(approved) =>
        logger.info(s"Filtering by approved=$approved")
        equal("approved", approved)
      case None =>
        logger.info("No filter applied")
        BsonDocument()
    }

    for {
      rows <- collection
        .find(filter)
        .skip((pagination.page - 1) * pagination.pageSize)
        .limit(pagination.pageSize)
        .toFuture()
        .map { results =>
          logger.info(s"Fetched ${results.size} rows from view")
          results
        }
      total <- collection
        .countDocuments(filter)
        .toFuture()
        .map { count =>
          logger.info(s"Total documents matching filter: $count")
          count
        }
    } yield {
      val paged = Paged(rows, pagination.page, pagination.pageSize, total)
      logger.info(s"Returning paged result: ${paged.size} results, page ${paged.pageIndex}, total ${paged.resultCount}")
      paged
    }
  }
}