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

package uk.gov.hmrc.bindingtariffclassification.service

import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.model.Role.CLASSIFICATION_OFFICER
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.repository._

import scala.concurrent.Future.{failed, successful}

class KeywordServiceSpec extends BaseSpec with BeforeAndAfterEach {

  private val keyword      = mock[Keyword]
  private val addedKeyword = mock[Keyword]

  private val keywordRepository = mock[KeywordsRepository]
  private val caseRepository    = mock[CaseRepository]

  private val pagination = mock[Pagination]

  private val caseHeader = CaseHeader(
    reference = "9999999999",
    Some(Operator("0", None, None, CLASSIFICATION_OFFICER, List(), List())),
    Some("3"),
    Some("Smartphone"),
    ApplicationType.BTI,
    CaseStatus.OPEN,
    0,
    None
  )

  private val caseHeader2 = CaseHeader(
    reference = "8888888888",
    Some(Operator("1", None, None, CLASSIFICATION_OFFICER, List(), List())),
    Some("4"),
    Some("Bicycle"),
    ApplicationType.BTI,
    CaseStatus.COMPLETED,
    0,
    None
  )

  private val caseKeyword  = CaseKeyword(Keyword("tool"), List(caseHeader))
  private val caseKeyword2 = CaseKeyword(Keyword("bike"), List(caseHeader))

  private val service =
    new KeywordService(keywordRepository, caseRepository)

  private final val emulatedFailure = new RuntimeException("Emulated failure.")

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(keywordRepository)
  }

  override protected def beforeEach(): Unit =
    super.beforeEach()

  "addKeyword" should {

    "return the keyword after it has being successfully added in the collection" in {
      when(keywordRepository.insert(keyword)).thenReturn(successful(addedKeyword))

      await(service.addKeyword(keyword)) shouldBe addedKeyword
    }

    "propagate any error" in {
      when(keywordRepository.insert(keyword)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.addKeyword(keyword))
      }
      caught shouldBe emulatedFailure
    }
  }

  "approveKeyword" should {

    "return the keyword after it has been updated in the database collection" in {
      when(keywordRepository.update(keyword, upsert = false))
        .thenReturn(successful(Some(addedKeyword)))

      await(service.approveKeyword(keyword, upsert = false)) shouldBe Some(addedKeyword)
    }

    "return None if the user does not exist in the database collection" in {
      when(keywordRepository.update(keyword, upsert = false))
        .thenReturn(successful(None))

      val result = await(service.approveKeyword(keyword, upsert = false))
      result shouldBe None
    }

    "propagate any error" in {
      when(keywordRepository.update(keyword, upsert = false))
        .thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.approveKeyword(keyword, upsert = false))
      }
      caught shouldBe emulatedFailure
    }
  }

  "delete" should {
    "return () and delegate to the repository" in {
      when(keywordRepository.delete(refEq("keyword name"))).thenReturn(successful(()))
      await(service.deleteKeyword("keyword name")) shouldBe ((): Unit)
      verify(keywordRepository, times(1)).delete(refEq("keyword name"))
    }

    "propagate any error" in {
      when(keywordRepository.delete(refEq("keyword name"))).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.deleteKeyword("keyword name"))
      }
      caught shouldBe emulatedFailure
    }
  }

  "findAll" should {
    "return the expected users" in {
      when(keywordRepository.findAll(pagination)).thenReturn(successful(Paged(Seq(keyword))))

      await(service.findAll(pagination)) shouldBe Paged(Seq(keyword))
    }

    "propagate any error" in {
      when(keywordRepository.findAll(pagination))
        .thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.findAll(pagination))
      }
      caught shouldBe emulatedFailure
    }
  }

  "fetchCaseKeywordAlternative" should {
    val keyword1      = Keyword("KEYWORD1", approved = true)
    val keyword2      = Keyword("KEYWORD2", approved = true)
    val pagination    = Pagination()
    val pagedKeywords = Paged(Seq(keyword1, keyword2), pagination, 2)

    val btApplication = mock[BTIApplication]
    when(btApplication.goodName).thenReturn("Goods1")

    val case1 = mock[Case]
    when(case1.reference).thenReturn("REF1")
    when(case1.assignee).thenReturn(Some(Operator("op1", Option("name1"))))
    when(case1.queueId).thenReturn(Some("queue1"))
    when(case1.status).thenReturn(CaseStatus.OPEN)
    when(case1.daysElapsed).thenReturn(10)
    when(case1.application).thenReturn(btApplication)

    val case2 = mock[Case]
    when(case2.reference).thenReturn("REF2")
    when(case2.assignee).thenReturn(Some(Operator("op2", Option("name2"))))
    when(case2.queueId).thenReturn(Some("queue2"))
    when(case2.status).thenReturn(CaseStatus.COMPLETED)
    when(case2.daysElapsed).thenReturn(20)
    when(case2.application).thenReturn(btApplication)

    val serviceWithSpy = spy(service)

    "fetch keywords and their associated cases" in {
      val pagedKeywords = Paged(Seq(Keyword("KEYWORD1"), Keyword("KEYWORD2")), pagination, 2)

      val caseKeyword1      = CaseKeyword(Keyword("KEYWORD1"), List(caseHeader))
      val caseKeyword2      = CaseKeyword(Keyword("KEYWORD2"), List(caseHeader2))
      val pagedCaseKeywords = Paged(Seq(caseKeyword1, caseKeyword2), pagination, 2)

      when(keywordRepository.findAll(any[Pagination])).thenReturn(successful(pagedKeywords))
      when(caseRepository.getGroupedCasesByKeyword(pagination)).thenReturn(successful(pagedCaseKeywords))

      val result = await(service.fetchCaseKeywords(pagination))

      result.pagedKeywords                               shouldBe pagedKeywords
      result.pagedCaseKeywords.results.size              shouldBe 2
      result.pagedCaseKeywords.results.map(_.keyword.name) should contain allOf ("KEYWORD1", "KEYWORD2")

      verify(keywordRepository).findAll(any[Pagination])
      verify(caseRepository).getGroupedCasesByKeyword(pagination)
    }

    "handle empty keyword list" in {
      val emptyPagedKeywords     = Paged(Seq.empty[Keyword], pagination, 0)
      val emptyPagedCaseKeywords = Paged(Seq.empty[CaseKeyword], pagination, 0)

      when(keywordRepository.findAll(any[Pagination])).thenReturn(successful(emptyPagedKeywords))
      when(caseRepository.getGroupedCasesByKeyword(pagination)).thenReturn(successful(emptyPagedCaseKeywords))

      val result = await(service.fetchCaseKeywords(pagination))

      result.pagedKeywords             shouldBe emptyPagedKeywords
      result.pagedCaseKeywords.results shouldBe empty
    }

    "propagate keyword repository errors" in {
      when(keywordRepository.findAll(any[Pagination])).thenReturn(failed(emulatedFailure))

      val caught = intercept[RuntimeException] {
        await(service.fetchCaseKeywords(pagination))
      }
      caught shouldBe emulatedFailure
    }
  }

}
