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

package uk.gov.hmrc.bindingtariffclassification.service

import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.crypto.Crypto
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.search.CaseParamsFilter
import uk.gov.hmrc.bindingtariffclassification.repository.{CaseRepository, SequenceRepository}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future.successful

class CaseServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val c1 = mock[Case]
  private val c1EncSaved = mock[Case]
  private val c1Enc = mock[Case]
  private val c1Dec = mock[Case]

  private val c2Enc = mock[Case]
  private val c2Dec = mock[Case]

  private val caseRepository = mock[CaseRepository]
  private val sequenceRepository = mock[SequenceRepository]
  private val eventService = mock[EventService]
  private val crypto = mock[Crypto]

  private val service = new CaseService(caseRepository, crypto, sequenceRepository, eventService)

  private final val emulatedFailure = new RuntimeException("Emulated failure.")

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(caseRepository, crypto)
  }

  "deleteAll()" should {

    "return () and clear the database collection" in {
      when(caseRepository.deleteAll()).thenReturn(successful(()))
      await(service.deleteAll()) shouldBe ((): Unit)
    }

    "propagate any error" in {
      when(caseRepository.deleteAll()).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.deleteAll())
      }
      caught shouldBe emulatedFailure
    }
  }

  "insert()" should {

    "return the case after it is inserted in the database collection" in {
      when(sequenceRepository.incrementAndGetByName("case")).thenReturn(successful(Sequence("case", 0)))
      when(crypto.encrypt(c1)).thenReturn(c1Enc)
      when(caseRepository.insert(c1Enc)).thenReturn(successful(c1EncSaved))
      when(crypto.decrypt(c1EncSaved)).thenReturn(c1Dec)

      val result = await(service.insert(c1))
      result shouldBe c1Dec
    }

    "propagate any error" in {
      when(sequenceRepository.incrementAndGetByName("case")).thenReturn(successful(Sequence("case", 0)))
      when(crypto.encrypt(c1)).thenReturn(c1Enc)
      when(caseRepository.insert(c1Enc)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.insert(c1))
      }
      caught shouldBe emulatedFailure
    }
  }

  "update()" should {

    "return the case after it is updated in the database collection" in {
      when(crypto.encrypt(c1)).thenReturn(c1Enc)
      when(caseRepository.update(c1Enc, upsert = false)).thenReturn(successful(Some(c1EncSaved)))
      when(crypto.decrypt(c1EncSaved)).thenReturn(c1Dec)

      val result = await(service.update(c1, upsert = false))
      result shouldBe Some(c1Dec)
    }

    "return None if the case does not exist in the database collection" in {
      when(crypto.encrypt(c1)).thenReturn(c1Enc)
      when(caseRepository.update(c1Enc, upsert = false)).thenReturn(successful(None))

      val result = await(service.update(c1, upsert = false))
      result shouldBe None
    }

    "propagate any error" in {
      when(crypto.encrypt(c1)).thenReturn(c1Enc)
      when(caseRepository.update(c1Enc, upsert = false)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.update(c1, upsert = false))
      }
      caught shouldBe emulatedFailure
    }

  }

  "getByReference()" should {

    "return the expected case" in {
      when(caseRepository.getByReference(c1.reference)).thenReturn(successful(Some(c1Enc)))
      when(crypto.decrypt(c1Enc)).thenReturn(c1Dec)

      val result = await(service.getByReference(c1.reference))
      result shouldBe Some(c1Dec)
    }

    "return None when the case is not found" in {
      when(caseRepository.getByReference(c1.reference)).thenReturn(successful(None))

      val result = await(service.getByReference(c1.reference))
      result shouldBe None
    }

    "propagate any error" in {
      when(caseRepository.getByReference(c1.reference)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.getByReference(c1.reference))
      }
      caught shouldBe emulatedFailure
    }

  }

  "get()" should {

    // TODO: test all possible combinations
    val nofilters = CaseParamsFilter()
    val nosorter = None

    "return the expected cases" in {
      when(caseRepository.get(nofilters, nosorter)).thenReturn(successful(Seq(c1Enc, c2Enc)))
      when(crypto.decrypt(c1Enc)).thenReturn(c1Dec)
      when(crypto.decrypt(c2Enc)).thenReturn(c2Dec)

      val result = await(service.get(nofilters, nosorter))
      result shouldBe Seq(c1Dec, c2Dec)
    }

    "return an empty sequence when there are no cases" in {
      when(caseRepository.get(nofilters, nosorter)).thenReturn(successful(Nil))
      val result = await(service.get(nofilters, nosorter))
      result shouldBe Nil
    }

    "propagate any error" in {
      when(caseRepository.get(nofilters, nosorter)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.get(nofilters, nosorter))
      }
      caught shouldBe emulatedFailure
    }

  }

  "incrementDaysElapsed()" should {
    "delegate to Repository" in {
      when(caseRepository.incrementDaysElapsed(1)).thenReturn(successful(1))
      await(service.incrementDaysElapsed(1)) shouldBe 1
    }
  }

}
