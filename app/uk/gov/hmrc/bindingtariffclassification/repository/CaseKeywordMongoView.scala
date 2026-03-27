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
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import play.api.Logging
import play.api.libs.json.Json
import uk.gov.hmrc.bindingtariffclassification.model.{CaseKeywordRow, MongoCodecs, Paged, Pagination}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs.toBson

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CaseKeywordMongoView @Inject() (
  mongoComponent: MongoComponent
)(implicit ec: ExecutionContext)
    extends Logging {

  private[repository] val caseKeywordsViewName = "caseKeywords"
  private[repository] val collectionName       = "cases"

  private val viewInitialized: Future[Unit] = initView.map(_ => ())

  private lazy val collection: MongoCollection[CaseKeywordRow] = mongoComponent.database
    .getCollection[CaseKeywordRow](caseKeywordsViewName)
    .withCodecRegistry(MongoCodecs.caseKeyword)

  private def pipeline: Seq[BsonDocument] = {
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
        "$project" -> Json.obj(
          "_id"             -> 0,
          "keyword"         -> "$keywords",
          "reference"       -> "$reference",
          "user"            -> "$assignee.name",
          "goods"           -> "$application.goodName",
          "caseType"        -> "$application.type",
          "status"          -> "$status",
          "liabilityStatus" -> "$application.status",
          "daysElapsed"     -> "$daysElapsed"
        )
      ),
      Json.obj(
        "$sort" -> Json.obj(
          "keyword" -> 1
        )
      )
    )
    pipelineJson.value.map(v => toBson(v).asDocument()).toSeq
  }

  private[repository] def createView(viewName: String, viewOn: String): Future[Unit] =
    mongoComponent.database
      .createView(viewName, viewOn, pipeline)
      .toFuture()
      .map(_ => ())
      .recover { case ex: Exception =>
        logger.error(s"Failed to create view '$viewName': ${ex.getMessage}", ex)
        throw ex
      }

  private[repository] def dropView(viewName: String): Future[Unit] =
    mongoComponent.database
      .getCollection(viewName)
      .drop()
      .toFuture()
      .map(_ => ())
      .recover { case _: Exception => () }

  private[repository] def getView(viewName: String): MongoCollection[CaseKeywordRow] =
    mongoComponent.database
      .getCollection[CaseKeywordRow](viewName)
      .withCodecRegistry(MongoCodecs.caseKeyword)

  private[repository] def initView: Future[MongoCollection[CaseKeywordRow]] =
    mongoComponent.database
      .listCollectionNames()
      .toFuture()
      .flatMap { collections =>
        if (collections.contains(caseKeywordsViewName))
          dropView(caseKeywordsViewName)
        else
          Future.successful(())
      }
      .flatMap(_ => createView(caseKeywordsViewName, collectionName))
      .map(_ => getView(caseKeywordsViewName))
      .recoverWith { case ex: Exception =>
        logger.error(s"Failed to initialize view: ${ex.getMessage}", ex)
        Future.failed(ex)
      }

  def fetchKeywordsFromCases(pagination: Pagination): Future[Paged[CaseKeywordRow]] =
    for {
      _ <- viewInitialized
      rows <- collection
                .find()
                .skip((pagination.page - 1) * pagination.pageSize)
                .limit(pagination.pageSize)
                .toFuture()
      total <- collection.countDocuments().toFuture()
    } yield Paged(rows, pagination.page, pagination.pageSize, total)
}
