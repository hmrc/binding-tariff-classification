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

import java.time.{Clock, LocalDate, ZoneId}
import java.util.UUID

import org.mockito.{ArgumentMatcher, ArgumentMatchers, Mockito}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.connector.BankHolidaysConnector
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.search.CaseParamsFilter
import uk.gov.hmrc.bindingtariffclassification.repository.{CaseRepository, SequenceRepository}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future.successful

class CaseServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val c1 = mock[Case]
  private val c1Saved = mock[Case]
  private val c2 = mock[Case]

  private val reference = UUID.randomUUID().toString
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val caseRepository = mock[CaseRepository]
  private val sequenceRepository = mock[SequenceRepository]
  private val eventService = mock[EventService]
  private val bankHolidaysConnector = mock[BankHolidaysConnector]

  private val service = new CaseService(caseRepository, sequenceRepository, eventService, bankHolidaysConnector)

  final val emulatedFailure = new RuntimeException("Emulated failure.")

  override protected def afterEach(): Unit = {
    reset(caseRepository, bankHolidaysConnector)
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
      val result = await(service.insert(c1))
      result shouldBe c1Saved
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
      when(caseRepository.update(c1)).thenReturn(successful(Some(c1)))
      val result = await(service.update(c1))
      result shouldBe Some(c1)
    }

    "return None if the case does not exist in the database collection" in {
      when(caseRepository.update(c1)).thenReturn(successful(None))
      val result = await(service.update(c1))
      result shouldBe None
    }

    "propagate any error" in {
      when(caseRepository.update(c1)).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.update(c1))
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

    // TODO: test all possible combinations
    val nofilters = CaseParamsFilter()
    val nosorter = None

    "return the expected cases" in {

      when(caseRepository.get(nofilters, nosorter)).thenReturn(successful(Seq(c1, c2)))
      val result = await(service.get(nofilters, nosorter))
      result shouldBe Seq(c1, c2)
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

  "incrementDaysElapsedIfAppropriate()" should {
    "Do nothing on a Saturday" in {
      val clock = givenTheDateIsFixedAt("2018-12-29")
      await(service.incrementDaysElapsedIfAppropriate(1, clock)) shouldBe 0
      verifyZeroInteractions(caseRepository)
    }

    "Do nothing on a Sunday" in {
      val clock = givenTheDateIsFixedAt("2018-12-30")
      await(service.incrementDaysElapsedIfAppropriate(1, clock)) shouldBe 0
      verifyZeroInteractions(caseRepository)
    }

    "Do nothing on a Bank Holiday" in {
      givenABankHolidayOn("2018-12-25")
      val clock = givenTheDateIsFixedAt("2018-12-25")
      await(service.incrementDaysElapsedIfAppropriate(1, clock)) shouldBe 0
      verifyZeroInteractions(caseRepository)
    }

    "Delegate to Repository" in {
      givenItIsNotABankHoliday()
      val clock = givenTheDateIsFixedAt("2018-12-24")
      when(caseRepository.incrementDaysElapsed(1)).thenReturn(successful(1))
      await(service.incrementDaysElapsedIfAppropriate(1, clock)) shouldBe 1
    }
  }

  private def givenABankHolidayOn(date: String): Unit = {
    when(bankHolidaysConnector.get()(ArgumentMatchers.any[HeaderCarrier])).thenReturn(Seq(LocalDate.parse(date)))
  }

  private def givenItIsNotABankHoliday(): Unit = {
    when(bankHolidaysConnector.get()(ArgumentMatchers.any[HeaderCarrier])).thenReturn(Seq.empty)
  }

  private def givenTheDateIsFixedAt(date: String) : Clock = {
    val zone = ZoneId.of("UTC")
    val instant = LocalDate.parse(date).atStartOfDay(zone).toInstant
    Clock.fixed(instant, zone)
  }

}
