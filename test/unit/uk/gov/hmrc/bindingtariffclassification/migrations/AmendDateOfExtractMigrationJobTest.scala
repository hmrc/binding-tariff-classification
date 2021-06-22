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

package uk.gov.hmrc.bindingtariffclassification.migrations

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.BDDMockito.`given`
import org.mockito.Mockito.{never, reset, times, verify}
import org.mockito.invocation.InvocationOnMock
import org.scalatest.BeforeAndAfterEach
import play.api.test.Helpers. _
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.bindingtariffclassification.sort.CaseSortField
import util.CaseData

import java.time.{LocalDate, ZoneOffset}
import scala.concurrent.Future

// scalastyle:off magic.number
class AmendDateOfExtractMigrationJobTest extends BaseSpec with BeforeAndAfterEach {

  private val caseService = mock[CaseService]
  private val caseSearch = CaseSearch(
    filter = CaseFilter(migrated = Some(true)),
    sort   = Some(CaseSort(Set(CaseSortField.REFERENCE)))
  )

  private def migrationJob: AmendDateOfExtractMigrationJob =
    new AmendDateOfExtractMigrationJob(caseService)

  override def afterEach(): Unit = {
    super.afterEach()
    reset(caseService)
  }

  "Scheduled Job" should {
    "Configure 'Name'" in {
      migrationJob.name shouldBe "AmendDateOfExtract"
    }
  }

  "Scheduled Job 'Execute'" should {

    "Update Date Of Extract - for no cases" in {
      givenAPageOfCases(1, 1, 0)

      await(migrationJob.execute())

      verifyNoCasesUpdated
    }

    "Update Date Of Extract - for case extracted on 02/10/2018" in {
      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aMigratedCaseWith(reference = "reference", dateOfExtract = LocalDate.of(2018, 10, 2)))

      await(migrationJob.execute())

      verifyNoCasesUpdated
    }

    "Update Date Of Extract - for case extracted on 01/01/2021" in {
      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aMigratedCaseWith(reference = "reference", dateOfExtract = LocalDate.of(2021, 1, 1)))

      await(migrationJob.execute())

      theCasesUpdated.dateOfExtract shouldBe Some(LocalDate.of(2020, 12, 31).atStartOfDay(ZoneOffset.UTC).toInstant)
    }

    "Update Date Of Extract - for multiple pages of cases" in {
      givenUpdatingACaseReturnsItself()
      givenPagesOfCases(
        Seq(
          aMigratedCaseWith(reference = "reference-1", dateOfExtract = LocalDate.of(2018, 10, 2)),
          aMigratedCaseWith(reference = "reference-2", dateOfExtract = LocalDate.of(2021, 1, 1)),
          aMigratedCaseWith(reference = "reference-3", dateOfExtract = LocalDate.of(2020, 12, 31))
        )
      )

      await(migrationJob.execute())

      verify(caseService, times(1)).update(any[Case], refEq(false))
      val updatedCase = theCasesUpdated
      updatedCase.reference     shouldBe "reference-2"
      updatedCase.dateOfExtract shouldBe Some(LocalDate.of(2020, 12, 31).atStartOfDay(ZoneOffset.UTC).toInstant)
    }
  }

  "Scheduled job 'Rollback'" should {
    "Update Date Of Extract - for no cases" in {
      givenAPageOfCases(1, 1, 0)

      await(migrationJob.rollback())

      verifyNoCasesUpdated
    }

    "Update Date Of Extract - for case extracted on 02/10/2018" in {
      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aMigratedCaseWith(reference = "reference", dateOfExtract = LocalDate.of(2018, 10, 2)))

      await(migrationJob.rollback())

      verifyNoCasesUpdated
    }

    "Update Date Of Extract - for case extracted on 01/01/2021" in {
      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aMigratedCaseWith(reference = "reference", dateOfExtract = LocalDate.of(2021, 1, 1)))

      await(migrationJob.rollback())

      verifyNoCasesUpdated
    }

    "Update Date Of Extract - for case extracted on 31/12/2020" in {
      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aMigratedCaseWith(reference = "reference", dateOfExtract = LocalDate.of(2020, 12, 31)))

      await(migrationJob.rollback())

      theCasesUpdated.dateOfExtract shouldBe Some(LocalDate.of(2021, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant)
    }

    "Update Date Of Extract - for multiple pages of cases" in {
      givenUpdatingACaseReturnsItself()
      givenPagesOfCases(
        Seq(
          aMigratedCaseWith(reference = "reference-1", dateOfExtract = LocalDate.of(2018, 10, 2)),
          aMigratedCaseWith(reference = "reference-2", dateOfExtract = LocalDate.of(2021, 1, 1)),
          aMigratedCaseWith(reference = "reference-3", dateOfExtract = LocalDate.of(2020, 12, 31))
        )
      )

      await(migrationJob.rollback())

      verify(caseService, times(1)).update(any[Case], refEq(false))
      val updatedCase = theCasesUpdated
      updatedCase.reference     shouldBe "reference-3"
      updatedCase.dateOfExtract shouldBe Some(LocalDate.of(2021, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant)
    }
  }

  private def verifyNoCasesUpdated =
    verify(caseService, never()).update(any[Case], any[Boolean])

  private def theCasesUpdated: Case = {
    val captor: ArgumentCaptor[Case] = ArgumentCaptor.forClass(classOf[Case])
    verify(caseService).update(captor.capture(), refEq(false))
    captor.getValue
  }

  private def givenAPageOfCases(page: Int, pageSize: Int, totalCases: Int, cases: Case*): Unit = {
    val pagination = Pagination(page = page)
    given(caseService.get(caseSearch, pagination)) willReturn
      Future.successful(Paged(cases, Pagination(page = page, pageSize = pageSize), totalCases))
  }

  private def givenPagesOfCases(cases: Seq[Case]): Unit =
    cases.zipWithIndex.foreach {
      case (cse, index) => givenAPageOfCases(index + 1, 1, cases.size, cse)
    }

  private def aMigratedCaseWith(reference: String, dateOfExtract: LocalDate): Case =
    CaseData
      .createCase()
      .copy(
        reference     = reference,
        dateOfExtract = Some(dateOfExtract.atStartOfDay(ZoneOffset.UTC).toInstant)
      )

  private def givenUpdatingACaseReturnsItself(): Unit =
    given(caseService.update(any[Case], any[Boolean])).will((invocation: InvocationOnMock) =>
      Future.successful(Option(invocation.getArgument[Case](0))))
}
