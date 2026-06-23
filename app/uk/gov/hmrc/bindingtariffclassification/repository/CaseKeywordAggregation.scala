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

import org.mongodb.scala.model.Filters.{empty, equal, in, not}
import org.mongodb.scala.model.Sorts.ascending
import org.mongodb.scala.model.{Filters, Sorts}
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import uk.gov.hmrc.bindingtariffclassification.model.*

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CaseKeywordAggregation @Inject() (
  keywordsRepository: KeywordsMongoRepository,
  viewUpdater: CaseKeywordViewUpdater
)(implicit ec: ExecutionContext) {

  def fetchKeywordsFromCases(pagination: Pagination): Future[Paged[CaseKeyword]] = {
    val skipCount  = (pagination.page - 1) * pagination.pageSize
    val limitCount = pagination.pageSize

    keywordsRepository.collection.find(equal("approved", true)).toFuture().flatMap { approvedKeywords =>
      val approvedNames  = approvedKeywords.map(_.name)
      val filterCriteria = if (approvedNames.nonEmpty) not(in("keyword", approvedNames*)) else empty()

      val totalCountFuture = viewUpdater.collection.countDocuments(filterCriteria).toFuture()

      val dataFuture = viewUpdater.collection
        .find(filterCriteria)
        .sort(ascending("keyword", "reference"))
        .skip(skipCount)
        .limit(limitCount)
        .toFuture()

      for {
        totalCount <- totalCountFuture
        rows       <- dataFuture
      } yield {
        val results = rows.map { row =>
          CaseKeyword(
            keyword = Keyword(name = row.keyword),
            cases = List(
              CaseHeader(
                reference = row.reference,
                status = CaseStatus.withName(row.status),
                assignee = row.assignee.map(id => Operator(id)),
                team = row.team,
                goodsName = row.goodsName,
                caseType = ApplicationType.withName(row.caseType.getOrElse("BTI")),
                daysElapsed = row.daysElapsed.toLong,
                liabilityStatus = row.liabilityStatus.map(s => LiabilityStatus.withName(s))
              )
            )
          )
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
