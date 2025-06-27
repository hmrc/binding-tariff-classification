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

package uk.gov.hmrc.bindingtariffclassification.repository

import cats.data.NonEmptySeq
import org.mockito.Mockito
import org.mockito.Mockito.{verify, when}
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.bindingtariffclassification.crypto.Crypto
import uk.gov.hmrc.bindingtariffclassification.model.ApplicationType.BTI
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.reporting._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future.successful

class EncryptedCaseMongoRepositoryTest extends BaseMongoIndexSpec with BeforeAndAfterEach {

  private val rawCase            = mock[Case]
  private val rawCaseSaved       = mock[Case]
  private val encryptedCase      = mock[Case]
  private val encryptedCaseSaved = mock[Case]
  private val search             = CaseSearch()
  private val pagination         = mock[Pagination]
  private val crypto             = mock[Crypto]
  private val underlyingRepo     = mock[CaseMongoRepository]
  private val repo               = new EncryptedCaseMongoRepository(underlyingRepo, crypto)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    when(crypto.encrypt(rawCase)).thenReturn(encryptedCase)
    when(crypto.decrypt(encryptedCaseSaved)).thenReturn(rawCaseSaved)
    ()
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    Mockito.reset(underlyingRepo)
  }

  "Insert" should {
    "Encrypt and delegate to Repository" in {
      when(underlyingRepo.insert(encryptedCase)).thenReturn(successful(encryptedCaseSaved))
      await(repo.insert(rawCase)) shouldBe rawCaseSaved
    }
  }

  "Update" should {
    "Encrypt and delegate to Repository" in {
      when(underlyingRepo.update(encryptedCase, upsert = true)).thenReturn(successful(Some(encryptedCaseSaved)))
      await(repo.update(rawCase, upsert = true)) shouldBe Some(rawCaseSaved)
    }

    "By reference should encrypt and delegate to Repository" in {
      val caseReferenceField = "reference"
      when(underlyingRepo.update(caseReferenceField, CaseUpdate())).thenReturn(successful(Some(encryptedCaseSaved)))
      await(repo.update(caseReferenceField, CaseUpdate())) shouldBe Some(rawCaseSaved)
    }
  }

  "Get By Reference" should {
    "Encrypt and delegate to Repository" in {
      when(underlyingRepo.getByReference("ref")).thenReturn(successful(Some(encryptedCaseSaved)))
      await(repo.getByReference("ref")) shouldBe Some(rawCaseSaved)
    }
  }

  "Get" should {
    "Encrypt and delegate to Repository" in {
      when(underlyingRepo.get(search, pagination)).thenReturn(successful(Paged(Seq(encryptedCaseSaved))))
      await(repo.get(search, pagination)) shouldBe Paged(Seq(rawCaseSaved))
    }

    "getAllByEori" in {
      val eori          = "eori"
      val encryptedEori = "eoriEncrypted"
      when(crypto.encryptString).thenReturn(_ => encryptedEori)
      when(underlyingRepo.getAllByEori(encryptedEori)).thenReturn(successful(List(encryptedCaseSaved)))
      await(repo.getAllByEori(eori)) shouldBe List(rawCaseSaved)
    }
  }

  "Delete" should {
    "Delete All will delegate to Repository" in {
      when(underlyingRepo.deleteAll()).thenReturn(successful((): Unit))
      await(repo.deleteAll())
      verify(underlyingRepo).deleteAll()
    }
    "Delete By Reference will delegate to Repository" in {
      val reference = "reference"
      when(underlyingRepo.delete(reference)).thenReturn(successful((): Unit))
      await(repo.delete(reference))
      verify(underlyingRepo).delete(reference)
    }
  }

  "Report" should {
    val pagination  = Pagination()
    val elapsedDays = 8
    "Summary Report delegate to repository" in {
      val summaryReport = SummaryReport(
        groupBy = NonEmptySeq.one(ReportField.Status),
        sortBy = ReportField.Count
      )
      when(underlyingRepo.summaryReport(summaryReport, pagination)).thenReturn(
        successful(
          Paged[ResultGroup](
            Seq(
              SimpleResultGroup(
                count = 2,
                groupKey = NonEmptySeq.one(ReportField.Status.withValue(Some(PseudoCaseStatus.COMPLETED))),
                maxFields = List(ReportField.ElapsedDays.withValue(Some(elapsedDays)))
              )
            )
          )
        )
      )
      await(repo.summaryReport(summaryReport, pagination))
      verify(underlyingRepo).summaryReport(summaryReport, pagination)
    }

    "Case Report delegate to repository" in {
      when(crypto.decryptString).thenReturn(_ => "raw field")

      val caseReport = CaseReport(
        fields = NonEmptySeq.of(ReportField.Status, ReportField.ContactName, ReportField.ContactEmail),
        sortBy = ReportField.Count
      )
      when(underlyingRepo.caseReport(caseReport, pagination)).thenReturn(
        successful(
          Paged[Map[String, ReportResultField[?]]](
            Seq(
              Map(
                "field1" -> NumberResultField(
                  "field",
                  None
                )
              ),
              Map(
                "contact-name" -> StringResultField(
                  "contact-name",
                  Option("raw field")
                )
              )
            )
          )
        )
      )
      await(repo.caseReport(caseReport, pagination))
      verify(underlyingRepo).caseReport(caseReport, pagination)
    }

    "Queue Report delegate to repository" in {
      val queueReport = QueueReport()
      when(underlyingRepo.queueReport(queueReport, pagination)).thenReturn(
        successful(
          Paged[QueueResultGroup](Seq(QueueResultGroup(1, None, BTI)))
        )
      )
      await(repo.queueReport(queueReport, pagination))
      verify(underlyingRepo).queueReport(queueReport, pagination)
    }
  }

}
