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

package uk.gov.hmrc.bindingtariffclassification.service

import java.util.UUID

import org.mockito.Mockito.{never, reset, times, verify, when}
import org.mockito.ArgumentMatchers.{any, anyString, refEq}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.{Case, CaseStatus, CaseStatusChange, Event}
import uk.gov.hmrc.bindingtariffclassification.model.search.CaseParamsFilter
import uk.gov.hmrc.bindingtariffclassification.repository.CaseRepository
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future.successful

class CaseServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  final private val c1 = mock[Case]
  final private val c2 = mock[Case]

  final private val reference = UUID.randomUUID().toString

  private val repository = mock[CaseRepository]
  private val eventService = mock[EventService]

  private val service = new CaseService(repository, eventService)

  final val emulatedFailure = new RuntimeException("Emulated failure.")

  override protected def beforeEach(): Unit = {
    reset(repository)
  }

  "deleteAll()" should {

    "return () and clear the database collection" in {
      when(repository.deleteAll).thenReturn(successful(()))
      await(service.deleteAll) shouldBe ((): Unit)
    }

    "propagate any error" in {
      when(repository.deleteAll).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.deleteAll)
      }
      caught shouldBe emulatedFailure
    }
  }

  "insert()" should {

    "return the case after it is inserted in the database collection" in {
      when(repository.insert(c1)).thenReturn(successful(c1))
      val result = await(service.insert(c1))
      result shouldBe c1
    }

    "propagate any error" in {
      when(repository.insert(c1)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.insert(c1))
      }
      caught shouldBe emulatedFailure
    }

  }

  "update()" should {

    "return the case after it is updated in the database collection" in {
      when(repository.update(c1)).thenReturn(successful(Some(c1)))
      val result = await(service.update(c1))
      result shouldBe Some(c1)
    }

    "return None if the case does not exist in the database collection" in {
      when(repository.update(c1)).thenReturn(successful(None))
      val result = await(service.update(c1))
      result shouldBe None
    }

    "propagate any error" in {
      when(repository.update(c1)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.update(c1))
      }
      caught shouldBe emulatedFailure
    }

  }

  "updateStatus()" should {

    "return None if there are no cases with the specified reference or if the case has already the status updated" in {
      when(repository.updateStatus(anyString, any[CaseStatus])).thenReturn(successful(None))

      val result = await(service.updateStatus(reference, CaseStatus.CANCELLED))
      result shouldBe None

      verify(eventService, never).insert(any[Event])
    }

    "propagate any error" in {
      when(repository.updateStatus(anyString, any[CaseStatus])).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.updateStatus(reference, CaseStatus.CANCELLED))
      }
      caught shouldBe emulatedFailure

      verify(eventService, never).insert(any[Event])
    }

    "return the original and the new cases after the status update" in {
      val newStatus = CaseStatus.OPEN

      when(c1.reference).thenReturn(reference)
      when(c1.status).thenReturn(CaseStatus.NEW)

      val e = Event(
        id = UUID.randomUUID().toString,
        details = CaseStatusChange(from = c1.status, to = newStatus),
        userId = "0", // TODO: this needs to be the currently loggedIn user
        caseReference = c1.reference)

      val eventFieldsToExcludeInTheInsertion = List("timestamp", "id")
      when(eventService.insert(refEq(e, eventFieldsToExcludeInTheInsertion: _*))).thenReturn(successful(e))

      when(repository.updateStatus(reference, newStatus)).thenReturn(successful(Some(c1)))

      val result = await(service.updateStatus(reference, newStatus))
      result shouldBe Some((c1, c1.copy(status = newStatus)))

      verify(eventService, times(1)).insert(any[Event])
    }

  }

  "getByReference()" should {

    "return the expected case" in {
      when(repository.getByReference(c1.reference)).thenReturn(successful(Some(c1)))
      val result = await(service.getByReference(c1.reference))
      result shouldBe Some(c1)
    }

    "return None when the case is not found" in {
      when(repository.getByReference(c1.reference)).thenReturn(successful(None))
      val result = await(service.getByReference(c1.reference))
      result shouldBe None
    }

    "propagate any error" in {
      when(repository.getByReference(c1.reference)).thenThrow(emulatedFailure)

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

      when(repository.get(nofilters, nosorter)).thenReturn(successful(Seq(c1, c2)))
      val result = await(service.get(nofilters, nosorter))
      result shouldBe Seq(c1, c2)
    }

    "return an empty sequence when there are no cases" in {
      when(repository.get(nofilters, nosorter)).thenReturn(successful(Nil))
      val result = await(service.get(nofilters, nosorter))
      result shouldBe Nil
    }

    "propagate any error" in {
      when(repository.get(nofilters, nosorter)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.get(nofilters, nosorter))
      }
      caught shouldBe emulatedFailure
    }

  }

}
