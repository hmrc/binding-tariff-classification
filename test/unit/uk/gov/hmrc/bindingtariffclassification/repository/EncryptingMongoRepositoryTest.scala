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
import uk.gov.hmrc.bindingtariffclassification.config.{AppConfig, MongoEncryption}
import uk.gov.hmrc.bindingtariffclassification.crypto.Crypto
import uk.gov.hmrc.bindingtariffclassification.model.Case
import uk.gov.hmrc.bindingtariffclassification.model.search.Search
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future.successful

class EncryptingMongoRepositoryTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val encryptionEnabled = MongoEncryption(enabled = true, key = Some("1AW32543H!="))
  private val encryptionDisabled = MongoEncryption(key = None)

  private val rawCase = mock[Case]
  private val rawCaseSaved = mock[Case]
  private val encryptedCase = mock[Case]
  private val encryptedCaseSaved = mock[Case]
  private val rawSearch = mock[Search]
  private val encryptedSearch = mock[Search]

  private val appConfig = mock[AppConfig]
  private val crypto = mock[Crypto]
  private val underlyingRepo = mock[CaseMongoRepository]
  private val repo = new EncryptingMongoRepository(underlyingRepo, crypto, appConfig)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(crypto.encrypt(rawSearch)) willReturn encryptedSearch
    given(crypto.encrypt(rawCase)) willReturn encryptedCase
    given(crypto.decrypt(encryptedCaseSaved)) willReturn rawCaseSaved
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    Mockito.reset(underlyingRepo)
  }

  "Insert" should {
    "Delegate to Repository" in {
      givenEncryptionIsDisabled()
      given(underlyingRepo.insert(rawCase)) willReturn successful(rawCaseSaved)
      await(repo.insert(rawCase)) shouldBe rawCaseSaved
    }

    "Encrypt and delegate to Repository" in {
      givenEncryptionIsEnabled()
      given(underlyingRepo.insert(encryptedCase)) willReturn successful(encryptedCaseSaved)
      await(repo.insert(rawCase)) shouldBe rawCaseSaved
    }
  }

  "Update" should {
    "Delegate to Repository" in {
      givenEncryptionIsDisabled()
      given(underlyingRepo.update(rawCase, upsert = true)) willReturn successful(Some(rawCaseSaved))
      await(repo.update(rawCase, upsert = true)) shouldBe Some(rawCaseSaved)
    }

    "Encrypt and delegate to Repository" in {
      givenEncryptionIsEnabled()
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
    "Delegate to Repository" in {
      givenEncryptionIsDisabled()
      given(underlyingRepo.getByReference("ref")) willReturn successful(Some(rawCaseSaved))
      await(repo.getByReference("ref")) shouldBe Some(rawCaseSaved)
    }

    "Encrypt and delegate to Repository" in {
      givenEncryptionIsEnabled()
      given(underlyingRepo.getByReference("ref")) willReturn successful(Some(encryptedCaseSaved))
      await(repo.getByReference("ref")) shouldBe Some(rawCaseSaved)
    }
  }

  "Get" should {
    "Delegate to Repository" in {
      givenEncryptionIsDisabled()
      given(underlyingRepo.get(rawSearch)) willReturn successful(Seq(rawCaseSaved))
      await(repo.get(rawSearch)) shouldBe Seq(rawCaseSaved)
    }

    "Encrypt and delegate to Repository" in {
      givenEncryptionIsEnabled()
      given(underlyingRepo.get(encryptedSearch)) willReturn successful(Seq(encryptedCaseSaved))
      await(repo.get(rawSearch)) shouldBe Seq(rawCaseSaved)
    }
  }

  "Delete All" should {
    "Delegate to Repository" in {
      given(underlyingRepo.deleteAll()) willReturn successful((): Unit)
      await(repo.deleteAll())
      verify(underlyingRepo).deleteAll()
    }
  }

  private def givenEncryptionIsEnabled(): Unit = {
    given(appConfig.mongoEncryption) willReturn encryptionEnabled
  }

  private def givenEncryptionIsDisabled(): Unit = {
    given(appConfig.mongoEncryption) willReturn encryptionDisabled
  }
}
