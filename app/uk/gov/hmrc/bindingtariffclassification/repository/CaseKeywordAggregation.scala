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
import org.mongodb.scala.bson.{BsonArray, BsonDocument, BsonInt32, BsonString, Document}
import org.mongodb.scala.model.Accumulators.push
import org.mongodb.scala.model.Aggregates.*
import org.mongodb.scala.model.Projections.include
import org.mongodb.scala.model.{Field, Filters, Sorts}
import org.mongodb.scala.{MongoCollection, ObservableFuture, SingleObservableFuture, documentToUntypedDocument}
import play.api.libs.json.Json
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatCaseKeyword
import uk.gov.hmrc.bindingtariffclassification.model.{CaseKeyword, MongoCodecs, Paged, Pagination}
import uk.gov.hmrc.bindingtariffclassification.repository.BaseMongoOperations.countField
import uk.gov.hmrc.mongo.MongoComponent

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

@Singleton
class CaseKeywordAggregation @Inject() (mongoComponent: MongoComponent)(implicit
  ec: ExecutionContext
) {

  def fetchKeywordsFromCases(pagination: Pagination): Future[Paged[CaseKeyword]] = {
    val skipCount  = (pagination.page - 1) * pagination.pageSize
    val limitCount = pagination.pageSize

    // 1. Önce onaylıları çekiyoruz
    mongoComponent.database
      .getCollection[Document]("keywords")
      .find(Filters.equal("approved", true))
      .projection(include("name"))
      .toFuture()
      .flatMap { approvedKeywordDocs =>
        val approvedNames = approvedKeywordDocs.map(_.getString("name")).filter(_ != null).toList

        val totalCountFuture = mongoComponent.database
          .getCollection[Document]("cases")
          .aggregate(
            Seq(
              project(include("keywords")),
              unwind("$keywords"),
              `match`(Filters.not(Filters.in("keywords", approvedNames: _*))),
              count(countField)
            )
          )
          .headOption()
          .map {
            case Some(doc) =>
              doc
                .get[org.mongodb.scala.bson.BsonNumber](countField)
                .map(_.doubleValue().toLong)
                .getOrElse(0L)
            case None => 0L
          }

        val rawDataFuture = mongoComponent.database
          .getCollection[Document]("cases")
          .aggregate(
            Seq(
              unwind("$keywords"),
              `match`(Filters.not(Filters.in("keywords", approvedNames: _*))),
              skip(skipCount),
              limit(limitCount),
              lookup("keywords", "keywords", "name", "keywordMeta"),
              project(
                BsonDocument(
                  "keyword" -> BsonDocument("name" -> "$keywords"),
                  "cases" -> BsonArray(
                    BsonDocument(
                      "reference"       -> "$reference",
                      "status"          -> "$status",
                      "assignee"        -> "$assignee",
                      "team"            -> "$queueId",
                      "goodsName"       -> "$application.goodName",
                      "caseType"        -> "$application.type",
                      "daysElapsed"     -> "$daysElapsed",
                      "liabilityStatus" -> "$application.status"
                    )
                  )
                )
              )
            )
          )
          .allowDiskUse(true)
          .toFuture()

        for {
          totalCount <- totalCountFuture
          rawData    <- rawDataFuture
        } yield {
          val results = rawData.map { doc =>
            val jsonString = doc.toJson()
            Json.parse(jsonString).as[CaseKeyword]
          }.toList

          Paged(
            results = results,
            pagination = pagination,
            resultCount = totalCount
          )
        }
      }
  }
}
