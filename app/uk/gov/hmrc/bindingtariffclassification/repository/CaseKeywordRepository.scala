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

import org.mongodb.scala.{MongoCollection, ObservableFuture}
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Accumulators.push
import org.mongodb.scala.model.Projections.include
import org.mongodb.scala.model.{Accumulators, Aggregates, Field, Projections}
import uk.gov.hmrc.bindingtariffclassification.model.{CaseKeyword, MongoCodecs, Paged, Pagination}
import uk.gov.hmrc.bindingtariffclassification.repository.BaseMongoOperations.{countField, pagedResults}
import uk.gov.hmrc.mongo.MongoComponent

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CaseKeywordRepository @Inject()(mongoComponent: MongoComponent)(implicit ec: ExecutionContext) {

  private val collectionName = "caseKeywords"

  // Cases collection with proper codec
  private val casesCollection: MongoCollection[?] =
    mongoComponent.database
      .getCollection("cases")
      .withCodecRegistry(MongoCodecs.caseCodec) // <-- make sure this exists

  // Keywords collection with proper codec
  private val keywordsCollection: MongoCollection[CaseKeyword] =
    mongoComponent.database
      .getCollection[CaseKeyword](collectionName)
      .withCodecRegistry(MongoCodecs.caseKeyword)

  /** Build the aggregation pipeline */
  private val keywordPipeline: Seq[Bson] = Seq(
    Aggregates.addFields(
      Field("team", "$queueId"),
      Field("goodsName", "$application.goodName"),
      Field("caseType", "$application.type"),
      Field("liabilityStatus", "$application.status")
    ),
    Aggregates.project(
      Projections.fields(
        include(
          "reference",
          "status",
          "assignee",
          "team",
          "goodsName",
          "caseType",
          "keywords",
          "daysElapsed",
          "liabilityStatus"
        )
      )
    ),
    Aggregates.unwind("$keywords"),
    Aggregates.group("$keywords", push("cases", "$$ROOT")),
    Aggregates.addFields(Field("keyword.name", "$_id")),
    Aggregates.project(BsonDocument("_id" -> 0))
  )

  /** Refresh the materialized keyword collection using $out */
  def refreshKeywords(): Future[Unit] = {
    val pipelineWithOut = keywordPipeline :+ Aggregates.out(collectionName)

    casesCollection
      .aggregate[BsonDocument](pipelineWithOut)
      .allowDiskUse(true)
      .toFuture()
      .map(_ => ())
  }

  /** Fetch keywords (paged) from the materialized collection */
  def fetchKeywordsFromCases(pagination: Pagination): Future[Paged[CaseKeyword]] = {
    val runAggregation = keywordsCollection
      .aggregate[CaseKeyword](
        Seq(
          Aggregates.project(BsonDocument("_id" -> 0)),
          Aggregates.skip((pagination.page - 1) * pagination.pageSize),
          Aggregates.limit(pagination.pageSize)
        )
      )
      .allowDiskUse(true)
      .toFuture()

    val futureCount = keywordsCollection
      .aggregate[BsonDocument](Seq(Aggregates.count(countField)))
      .allowDiskUse(true)
      .headOption()

    pagedResults(futureCount, runAggregation, pagination)
  }
}