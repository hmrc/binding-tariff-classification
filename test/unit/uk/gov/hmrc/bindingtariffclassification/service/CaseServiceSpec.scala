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
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.{CaseSearch, _}
import uk.gov.hmrc.bindingtariffclassification.repository.{CaseRepository, SequenceRepository}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future.successful

class CaseServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val c1 = mock[Case]
  private val c1Saved = mock[Case]

  private val caseRepository = mock[CaseRepository]
  private val sequenceRepository = mock[SequenceRepository]
  private val eventService = mock[EventService]
  private val appConfig = mock[AppConfig]
  private val service = new CaseService(appConfig, caseRepository, sequenceRepository, eventService)

  private final val emulatedFailure = new RuntimeException("Emulated failure.")

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(caseRepository)
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
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
      when(caseRepository.insert(c1)).thenReturn(successful(c1Saved))

      await(service.insert(c1)) shouldBe c1Saved
    }

    "propagate any error" in {
      when(sequenceRepository.incrementAndGetByName("case")).thenReturn(successful(Sequence("case", 0)))
      when(caseRepository.insert(c1)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.insert(c1))
      }
      caught shouldBe emulatedFailure
    }
  }

  "update()" should {

    "return the case after it is updated in the database collection" in {
      when(caseRepository.update(c1, upsert = false)).thenReturn(successful(Some(c1Saved)))

      await(service.update(c1, upsert = false)) shouldBe Some(c1Saved)
    }

    "return None if the case does not exist in the database collection" in {
      when(caseRepository.update(c1, upsert = false)).thenReturn(successful(None))

      val result = await(service.update(c1, upsert = false))
      result shouldBe None
    }

    "propagate any error" in {
      when(caseRepository.update(c1, upsert = false)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.update(c1, upsert = false))
      }
      caught shouldBe emulatedFailure
    }

  }

  "getByReference()" should {

    "return the expected case" in {
      when(caseRepository.getByReference(c1.reference)).thenReturn(successful(Some(c1)))

      val result = await(service.getByReference(c1.reference))
      result shouldBe Some(c1)
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
    val searchBy = mock[CaseSearch]
    val pagination = mock[Pagination]

    "return the expected cases" in {
      when(caseRepository.get(searchBy, pagination)).thenReturn(successful(Paged(Seq(c1))))

      await(service.get(searchBy, pagination)) shouldBe Paged(Seq(c1))
    }

    "propagate any error" in {
      when(caseRepository.get(searchBy, pagination)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.get(searchBy, pagination))
      }
      caught shouldBe emulatedFailure
    }

  }

}
