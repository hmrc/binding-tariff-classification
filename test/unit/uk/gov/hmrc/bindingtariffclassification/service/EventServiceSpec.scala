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
import uk.gov.hmrc.bindingtariffclassification.model.Event
import uk.gov.hmrc.bindingtariffclassification.repository.EventRepository
import uk.gov.hmrc.bindingtariffclassification.service.EventService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future.successful

class EventServiceSpec extends UnitSpec with MockitoSugar {

  final private val e1 = mock[Event]
  final private val e2 = mock[Event]

  private val repository = mock[EventRepository]
  private val service = new EventService(repository)

  final val emulatedFailure = new RuntimeException("Emulated failure.")

  "insert" should {

    "return the expected event after it is inserted in the database collection" in {
      Mockito.when(repository.insert(e1)).thenReturn(successful(e1))
      val result = await(service.insert(e1))
      result shouldBe e1
    }

    "propagate any error" in {
      Mockito.when(repository.insert(e1)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.insert(e1))
      }
      caught shouldBe emulatedFailure
    }
  }

  "getById" should {

    "return the expected event" in {
      Mockito.when(repository.getById(e1.id)).thenReturn(successful(Some(e1)))
      val result = await(service.getById(e1.id))
      result shouldBe Some(e1)
    }

    "return None when the event is not found" in {
      Mockito.when(repository.getById(e1.id)).thenReturn(successful(None))
      val result = await(service.getById(e1.id))
      result shouldBe None
    }

    "propagate any error" in {
      Mockito.when(repository.getById(e1.id)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.getById(e1.id))
      }
      caught shouldBe emulatedFailure
    }
  }

  "getByCaseReference" should {

    "return the expected events" in {
      Mockito.when(repository.getByCaseReference(e1.caseReference)).thenReturn(successful(Seq(e1, e2)))
      val result = await(service.getByCaseReference(e1.caseReference))
      result shouldBe Seq(e1, e2)
    }

    "return an empty sequence when events are not found" in {
      Mockito.when(repository.getByCaseReference(e1.caseReference)).thenReturn(successful(Seq()))
      val result = await(service.getByCaseReference(e1.caseReference))
      result shouldBe Nil
    }

    "propagate any error" in {
      Mockito.when(repository.getByCaseReference(e1.caseReference)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.getByCaseReference(e1.caseReference))
      }
      caught shouldBe emulatedFailure
    }
  }

}
