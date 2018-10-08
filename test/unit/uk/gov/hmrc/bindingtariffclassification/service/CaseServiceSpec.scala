/*
 * Copyright 2018 HM Revenue & Customs
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

package unit.uk.gov.hmrc.bindingtariffclassification.service

import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.model.Case
import uk.gov.hmrc.bindingtariffclassification.repository.CaseRepository
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future.successful

class CaseServiceSpec extends UnitSpec with MockitoSugar {

  final private val c1 = mock[Case]
  final private val c2 = mock[Case]

  private val repository = mock[CaseRepository]
  private val service = new CaseService(repository)

  final val emulatedFailure = new RuntimeException("Emulated failure.")

  "save" should {

    "return the case after it is inserted in the database collection" in {
      Mockito.when(repository.insertOrUpdate(c1)).thenReturn(successful((true, c1)))
      val result = await(service.save(c1))
      result shouldBe ((true, c1))
    }

    "return the case after it is updated in the database collection" in {
      Mockito.when(repository.insertOrUpdate(c1)).thenReturn(successful((false, c1)))
      val result = await(service.save(c1))
      result shouldBe ((false, c1))
    }

    "propagate any error" in {
      Mockito.when(repository.insertOrUpdate(c1)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.save(c1))
      }
      caught shouldBe emulatedFailure
    }
  }

  "getByReference" should {

    "return the expected event" in {
      Mockito.when(repository.getByReference(c1.reference)).thenReturn(successful(Some(c1)))
      val result = await(service.getByReference(c1.reference))
      result shouldBe Some(c1)
    }

    "return None when the event is not found" in {
      Mockito.when(repository.getByReference(c1.reference)).thenReturn(successful(None))
      val result = await(service.getByReference(c1.reference))
      result shouldBe None
    }

    "propagate any error" in {
      Mockito.when(repository.getByReference(c1.reference)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.getByReference(c1.reference))
      }
      caught shouldBe emulatedFailure
    }
  }

  "getAll" should {

    "return the expected cases" in {
      Mockito.when(repository.getAll).thenReturn(successful(Seq(c1, c2)))
      val result = await(service.getAll)
      result shouldBe Seq(c1, c2)
    }

    "return an empty sequence when there are no cases" in {
      Mockito.when(repository.getAll).thenReturn(successful(Seq()))
      val result = await(service.getAll)
      result shouldBe Nil
    }

    "propagate any error" in {
      Mockito.when(repository.getAll).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.getAll)
      }
      caught shouldBe emulatedFailure
    }
  }

}
