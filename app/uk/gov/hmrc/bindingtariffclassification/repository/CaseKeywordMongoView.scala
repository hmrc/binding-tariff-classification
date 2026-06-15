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
import org.mongodb.scala.{MongoCollection, ObservableFuture, SingleObservableFuture}
import play.api.libs.json.Json
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatCaseKeyword
import uk.gov.hmrc.bindingtariffclassification.model.{CaseKeyword, MongoCodecs, Paged, Pagination}
import uk.gov.hmrc.bindingtariffclassification.repository.BaseMongoOperations.countField
import uk.gov.hmrc.mongo.MongoComponent

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

@Singleton
class CaseKeywordMongoView @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext) {

  private[repository] val caseKeywordsViewName = "caseKeywordsRow"

  lazy val view: MongoCollection[CaseKeyword] = Await.result(awaitable = initView, atMost = 30.seconds)

  private[repository] def createView(viewName: String, viewOn: String): Future[Unit] =
    mongoComponent.database
      .createView(viewName, viewOn, pipeline)
      .toFuture()
      .map(_ => ())

  private[repository] def dropView(viewName: String): Future[Unit] =
    getView(viewName).drop().toFuture()

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
      lookup(
        "keywords",
        "keywords",
        "name",
        "keywordMeta"
      ),
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
      )
    )

  private val matchNotApproved: Bson =
    Filters.or(
      Filters.equal("approved", false),
      Filters.exists("approved", false)
    )

  private val excludeIdProjection  = BsonDocument("_id" -> 0)
  private val defaultCountResponse = BsonDocument(countField -> BsonInt32(0))

  private val approvedMetaExpression = BsonDocument(
    "$arrayElemAt" -> BsonArray(
      BsonString("$keywordMeta.approved"),
      BsonInt32(0)
    )
  )

  private val groupPreProjection = project(
    include(
      "keywords",
      "reference",
      "status",
      "assignee",
      "team",
      "goodsName",
      "caseType",
      "daysElapsed",
      "liabilityStatus",
      "keywordMeta"
    )
  )

  private val countPreProjection = project(include("approved"))

  def fetchKeywordsFromCases(pagination: Pagination): Future[Paged[CaseKeyword]] = {

    val runAggregation = mongoComponent.database
      .getCollection[Document](caseKeywordsViewName)
      .aggregate(
        Seq(
          `match`(matchNotApproved),
          sort(Sorts.ascending("keywords", "reference")),
          skip((pagination.page - 1) * pagination.pageSize),
          limit(pagination.pageSize),
          groupPreProjection,
          group(
            "$keywords",
            push(
              "cases",
              BsonDocument(
                "reference"       -> "$reference",
                "status"          -> "$status",
                "assignee"        -> "$assignee",
                "team"            -> "$team",
                "goodsName"       -> "$goodsName",
                "caseType"        -> "$caseType",
                "daysElapsed"     -> "$daysElapsed",
                "liabilityStatus" -> "$liabilityStatus"
              )
            )
          ),
          addFields(
            Field("keyword.name", "$_id"),
            Field("approved", approvedMetaExpression)
          ),
          project(excludeIdProjection)
        )
      )
      .allowDiskUse(true)
      .toFuture()

    val futureCount =
      view
        .aggregate[BsonDocument](
          Seq(
            `match`(matchNotApproved),
            countPreProjection,
            count(countField)
          )
        )
        .headOption()
        .map {
          case Some(doc) => doc
          case None      => defaultCountResponse
        }

    for {
      countDoc <- futureCount
      rawData  <- runAggregation
    } yield {
      val total = countDoc.getNumber(countField).longValue()
      val results = rawData.map { doc =>
        val jsonString = doc.toJson()
        Json.parse(jsonString).as[CaseKeyword]
      }.toList

      Paged(
        results = results,
        pagination = pagination,
        resultCount = total
      )
    }
  }
}
