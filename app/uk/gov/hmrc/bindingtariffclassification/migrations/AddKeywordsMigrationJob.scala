/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.migrations

import javax.inject.{Inject, Singleton}

import better.files._
import cats.syntax.all._
import uk.gov.hmrc.bindingtariffclassification.common.Logging
import uk.gov.hmrc.bindingtariffclassification.model.Keyword
import uk.gov.hmrc.bindingtariffclassification.service.KeywordService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.bindingtariffclassification.model.Pagination

@Singleton
class AddKeywordsMigrationJob @Inject() (
  keywordService: KeywordService
)(implicit ec: ExecutionContext)
    extends MigrationJob
    with Logging {

  override def name: String = "AddKeywords"

  override def execute(): Future[Unit] =
    keywordService.findAll(Pagination()).flatMap { page =>
      if (page.resultCount > 0)
        Future.unit
      else
        using(Resource.getAsStream("keywords.txt")) { stream =>
          val keywords = stream.lines
          keywords.toList.traverse_(keyword => keywordService.addKeyword(Keyword(keyword, approved = true)))
        }
    }

  override def rollback(): Future[Unit] =
    Future.unit
}
