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

import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.test.Helpers._
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.KeywordService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AddKeywordsMigrationJobSpec extends BaseSpec with BeforeAndAfterEach {
  val keywordService: KeywordService = mock[KeywordService]

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(keywordService)
  }

  "AddKeywordsMigrationJob" should {
    val addKeywordsJob = new AddKeywordsMigrationJob(keywordService)

    "not add keywords if any already exist" in {
      given(keywordService.findAll(any[Pagination])) willReturn Future.successful(
        Paged(Seq(Keyword("FOR STORAGE OF GOODS", approved = true)))
      )

      await(addKeywordsJob.execute())

      verify(keywordService, never()).addKeyword(any[Keyword])
    }

    "add keywords if they are missing" in {
      val noOfInvocations: Int = 10752
      given(keywordService.findAll(any[Pagination])) willReturn Future.successful(Paged.empty[Keyword])
      given(keywordService.addKeyword(any[Keyword])) willReturn Future.successful(Keyword("foo"))

      await(addKeywordsJob.execute())

      // The number of keywords in keywords.txt
      verify(keywordService, times(noOfInvocations)).addKeyword(any[Keyword])
    }
  }
}
