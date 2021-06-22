/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.test.Helpers. _
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.repository.{CaseAttachmentAggregation, CaseRepository, MigrationLockRepository, SequenceRepository}

import scala.concurrent.Future.successful

// scalastyle:off magic.number
class CaseServiceSpec extends BaseSpec with BeforeAndAfterEach {

  private val c1      = mock[Case]
  private val c1Saved = mock[Case]

  private val caseRepository            = mock[CaseRepository]
  private val sequenceRepository        = mock[SequenceRepository]
  private val migrationRepository       = mock[MigrationLockRepository]
  private val caseAttachmentAggregation = mock[CaseAttachmentAggregation]
  private val eventService              = mock[EventService]
  private val appConfig                 = mock[AppConfig]

  private val service =
    new CaseService(
      appConfig,
      caseRepository,
      sequenceRepository,
      migrationRepository,
      caseAttachmentAggregation,
      eventService
    )

  private final val emulatedFailure = new RuntimeException("Emulated failure.")

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(caseRepository, sequenceRepository, migrationRepository, caseAttachmentAggregation, appConfig)
  }

  override protected def beforeEach(): Unit =
    super.beforeEach()

  "nextCaseReference()" should {

    "generate a new reference" when {

      "Case is an ATaR" in {
        when(sequenceRepository.incrementAndGetByName("ATaR Case Reference"))
          .thenReturn(successful(Sequence("ATaR Case Reference", 10)))
        when(appConfig.atarCaseReferenceOffset).thenReturn(1000)
        await(service.nextCaseReference(ApplicationType.BTI)) shouldBe "1010"
      }

      "Case is a Liability" in {
        when(sequenceRepository.incrementAndGetByName("Other Case Reference"))
          .thenReturn(successful(Sequence("Other Case Reference", 5)))
        when(appConfig.otherCaseReferenceOffset).thenReturn(2000)
        await(service.nextCaseReference(ApplicationType.LIABILITY_ORDER)) shouldBe "2005"
      }
    }

  }

  "deleteAll()" should {

    "return () and clear the database collection" in {
      when(caseRepository.deleteAll()).thenReturn(successful(()))
      when(sequenceRepository.deleteSequenceByName("ATaR Case Reference")).thenReturn(successful(()))
      when(sequenceRepository.deleteSequenceByName("Other Case Reference")).thenReturn(successful(()))
      when(migrationRepository.deleteAll()).thenReturn(successful(()))
      await(service.deleteAll()) shouldBe ((): Unit)
      verify(caseRepository, times(1)).deleteAll()
      verify(sequenceRepository, times(1)).deleteSequenceByName("ATaR Case Reference")
      verify(sequenceRepository, times(1)).deleteSequenceByName("Other Case Reference")
      verify(migrationRepository, times(1)).deleteAll()
    }

    "propagate any error" in {
      when(caseRepository.deleteAll()).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.deleteAll())
      }
      caught shouldBe emulatedFailure
    }
  }

  "delete()" should {
    "return () and delegate to the repository" in {
      when(caseRepository.delete(refEq("ref"))).thenReturn(successful(()))
      await(service.delete("ref")) shouldBe ((): Unit)
      verify(caseRepository, times(1)).delete(refEq("ref"))
      verify(sequenceRepository, never()).deleteSequenceByName(any[String])
    }

    "propagate any error" in {
      when(caseRepository.delete(refEq("ref"))).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.delete("ref"))
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

  "update() with CaseUpdate" should {
    val caseUpdate = CaseUpdate()

    "return the case after it is updated in the database collection" in {
      when(caseRepository.update(c1.reference, caseUpdate)).thenReturn(successful(Some(c1Saved)))
      await(service.update(c1.reference, caseUpdate)) shouldBe Some(c1Saved)
    }

    "return None if the case does not exist in the database collection" in {
      when(caseRepository.update(c1.reference, caseUpdate)).thenReturn(successful(None))
      val result = await(service.update(c1.reference, caseUpdate))
      result shouldBe None
    }

    "propagate any error" in {
      when(caseRepository.update(c1.reference, caseUpdate)).thenThrow(emulatedFailure)
      val caught = intercept[RuntimeException] {
        await(service.update(c1.reference, caseUpdate))
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
    val searchBy   = mock[CaseSearch]
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

  "attachmentExists()" should {
    "return true if attachment was found" in {
      when(caseAttachmentAggregation.find("id")).thenReturn(successful(Some(mock[Attachment])))

      await(service.attachmentExists("id")) shouldBe true
    }

    "return false if no attachment was found" in {
      when(caseAttachmentAggregation.find("id")).thenReturn(successful(None))

      await(service.attachmentExists("id")) shouldBe false
    }

    "propagate any error" in {
      when(caseAttachmentAggregation.find("id")).thenThrow(emulatedFailure)

      val caught = intercept[RuntimeException] {
        await(service.attachmentExists("id"))
      }
      caught shouldBe emulatedFailure
    }
  }

}
