/*
 * Copyright 2019 HM Revenue & Customs
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

import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.crypto.Crypto
import uk.gov.hmrc.bindingtariffclassification.model.{Case, Paged, Pagination, Search}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future.successful

class EncryptedCaseMongoRepositoryTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val rawCase = mock[Case]
  private val rawCaseSaved = mock[Case]
  private val encryptedCase = mock[Case]
  private val encryptedCaseSaved = mock[Case]
  private val search = Search()
  private val pagination = mock[Pagination]
  private val crypto = mock[Crypto]
  private val underlyingRepo = mock[CaseMongoRepository]
  private val repo = new EncryptedCaseMongoRepository(underlyingRepo, crypto)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(crypto.encrypt(rawCase)) willReturn encryptedCase
    given(crypto.decrypt(encryptedCaseSaved)) willReturn rawCaseSaved
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    Mockito.reset(underlyingRepo)
  }

  "Insert" should {
    "Encrypt and delegate to Repository" in {
      given(underlyingRepo.insert(encryptedCase)) willReturn successful(encryptedCaseSaved)
      await(repo.insert(rawCase)) shouldBe rawCaseSaved
    }
  }

  "Update" should {
    "Encrypt and delegate to Repository" in {
      given(underlyingRepo.update(encryptedCase, upsert = true)) willReturn successful(Some(encryptedCaseSaved))
      await(repo.update(rawCase, upsert = true)) shouldBe Some(rawCaseSaved)
    }
  }

  "Increment Days Elapsed" should {
    "Delegate to Repository" in {
      given(underlyingRepo.incrementDaysElapsed(1)) willReturn successful(1)
      await(repo.incrementDaysElapsed(1)) shouldBe 1
    }
  }

  "Get By Reference" should {
    "Encrypt and delegate to Repository" in {
      given(underlyingRepo.getByReference("ref")) willReturn successful(Some(encryptedCaseSaved))
      await(repo.getByReference("ref")) shouldBe Some(rawCaseSaved)
    }
  }

  "Get" should {
    "Encrypt and delegate to Repository" in {
      given(underlyingRepo.get(search, pagination)) willReturn successful(Paged(Seq(encryptedCaseSaved)))
      await(repo.get(search, pagination)) shouldBe Paged(Seq(rawCaseSaved))
    }
  }

  "Delete All" should {
    "Delegate to Repository" in {
      given(underlyingRepo.deleteAll()) willReturn successful((): Unit)
      await(repo.deleteAll())
      verify(underlyingRepo).deleteAll()
    }
  }
}
