/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.service

import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.repository._

import javax.inject._
import scala.concurrent.Future

@Singleton
class KeywordService @Inject() (
  keywordRepository: KeywordsRepository,
  caseKeywordAggregation: CaseKeywordMongoView
) {

  def addKeyword(keyword: Keyword): Future[Keyword] =
    keywordRepository.insert(keyword)

  def approveKeyword(keyword: Keyword, upsert: Boolean): Future[Option[Keyword]] =
    keywordRepository.update(keyword, upsert)

  def findAll(pagination: Pagination): Future[Paged[Keyword]] =
    keywordRepository.findAll(pagination)

  def deleteKeyword(name: String): Future[Unit] =
    keywordRepository.delete(name)

  def fetchCaseKeywords(pagination: Pagination): Future[Paged[CaseKeyword]] =
    caseKeywordAggregation.fetchKeywordsFromCases(pagination)

}
