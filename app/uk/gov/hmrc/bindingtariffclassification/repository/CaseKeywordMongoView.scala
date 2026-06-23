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

import org.mongodb.scala.{MongoCollection, ObservableFuture, SingleObservableFuture, bsonDocumentToUntypedDocument}
import org.mongodb.scala.bson.{BsonArray, BsonDocument, BsonInt32, BsonString}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Accumulators.push
import org.mongodb.scala.model.Aggregates.*
import org.mongodb.scala.model.Projections.include
import org.mongodb.scala.model.{Field, Filters, Sorts}
import uk.gov.hmrc.bindingtariffclassification.model.{CaseKeyword, MongoCodecs, Paged, Pagination}
import uk.gov.hmrc.bindingtariffclassification.repository.BaseMongoOperations.countField
import uk.gov.hmrc.mongo.MongoComponent

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

@Singleton
class CaseKeywordMongoView @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext) {

  private[repository] val caseKeywordsViewName = "caseKeywords"

  lazy val view: MongoCollection[CaseKeyword] = Await.result(awaitable = initView, atMost = 30.seconds)

  private[repository] def createView(viewName: String, viewOn: String): Future[Unit] =
    mongoComponent.database
      .createView(viewName, viewOn, pipeline)
      .toFuture()
      .map(_ => ())

  private[repository] def dropView(viewName: String): Future[Unit] =
    getView(viewName)
      .drop()
      .toFuture()

  private[repository] def getView(viewName: String): MongoCollection[CaseKeyword] =
    mongoComponent.database
      .getCollection[CaseKeyword](viewName)
      .withCodecRegistry(MongoCodecs.caseKeyword)

  private[repository] def initView: Future[MongoCollection[CaseKeyword]] =
    dropView(caseKeywordsViewName)
      .flatMap(_ => createView(caseKeywordsViewName, "cases"))
      .map(_ => getView(caseKeywordsViewName))

  private val pipeline: Seq[Bson] =
    Seq(
      addFields(
        Field("team", "$queueId"),
        Field("goodsName", "$application.goodName"),
        Field("caseType", "$application.type"),
        Field("liabilityStatus", "$application.status")
      ),
      project(
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
      ),
      unwind("$keywords"),
      group(
        "$keywords",
        push("cases", "$$ROOT")
      ),
      sort(Sorts.ascending("_id")),
      lookup(
        "keywords",
        "_id",
        "name",
        "keywordMeta"
      ),
      addFields(Field("keyword.name", "$_id")),
      addFields(
        Field(
          "approved",
          BsonDocument(
            "$arrayElemAt" -> BsonArray(
              BsonString("$keywordMeta.approved"),
              BsonInt32(0)
            )
          )
        )
      ),
      project(BsonDocument("_id" -> 0))
    )

  private val matchNotApproved: Bson =
    Filters.or(
      Filters.equal("approved", false),
      Filters.exists("approved", false)
    )

  def fetchKeywordsFromCases(pagination: Pagination): Future[Paged[CaseKeyword]] = {
    val skipCount  = (pagination.page - 1) * pagination.pageSize
    val limitCount = pagination.pageSize

    val runAggregation = view
      .aggregate[CaseKeyword](
        Seq(
          `match`(matchNotApproved),
          unwind("$cases"),
          sort(
            Sorts.orderBy(
              Sorts.ascending("keyword.name"),
              Sorts.ascending("cases.reference")
            )
          ),
          skip(skipCount),
          limit(limitCount),
          group(
            id = "$keyword",
            push("cases", "$cases")
          ),
          sort(Sorts.ascending("_id.name")),
          project(
            BsonDocument(
              "_id" -> 0,
              "keyword" -> "$_id",
              "cases" -> "$cases"
            )
          )
        )
      )
      .allowDiskUse(true)
      .toFuture()

    val futureCount = view
      .aggregate[BsonDocument](
        Seq(
          `match`(matchNotApproved),
          unwind("$cases"),
          count(countField)
        )
      )
      .allowDiskUse(true)
      .headOption()
      .map {
        case Some(doc) => doc.getInteger(countField, 0).toLong
        case None      => 0L
      }

    for {
      total   <- futureCount
      results <- runAggregation
    } yield Paged(
      results = results,
      pagination = pagination,
      resultCount = total
    )
  }
}