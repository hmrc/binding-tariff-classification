/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.scheduler

import java.time._

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.config.{AppConfig, JobConfig}
import uk.gov.hmrc.bindingtariffclassification.connector.BankHolidaysConnector
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.{CaseService, EventService}
import uk.gov.hmrc.bindingtariffclassification.sort.CaseSortField
import uk.gov.hmrc.http.HeaderCarrier
import util.CaseData
import util.EventData.createCaseStatusChangeEventDetails

import scala.concurrent.Future
import scala.concurrent.duration._

class ActiveDaysElapsedJobTest extends BaseSpec with BeforeAndAfterEach {

  private val caseService = mock[CaseService]
  private val eventService = mock[EventService]
  private val bankHolidaysConnector = mock[BankHolidaysConnector]
  private val appConfig = mock[AppConfig]
  private val caseSearch = CaseSearch(
    filter = CaseFilter(statuses = Some(Set(PseudoCaseStatus.OPEN, PseudoCaseStatus.NEW))),
    sort = Some(CaseSort(Set(CaseSortField.REFERENCE)))
  )

  override def afterEach(): Unit = {
    super.afterEach()
    reset(appConfig, caseService, eventService)
  }

  "Scheduled Job" should {

    "Configure 'Name'" in {
      newJob.name shouldBe "ActiveDaysElapsed"
    }

    "Configure 'firstRunTime'" in {
      val runTime = LocalTime.of(14, 0)
      given(appConfig.activeDaysElapsed).willReturn(JobConfig(runTime, 1.day))

      newJob.firstRunTime shouldBe runTime
    }

    "Configure 'interval'" in {
      given(appConfig.activeDaysElapsed).willReturn(JobConfig(LocalTime.MIDNIGHT, 1.day))

      newJob.interval shouldBe 1.day
    }

  }

  "Scheduled Job 'Execute'" should {

    "Update Days Elapsed - for no cases" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-01T00:00:00")

      givenAPageOfCases(1, 1, 0)

      await(newJob.execute())
    }

    "Update Days Elapsed - for case created today" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-01T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenThereAreNoEventsFor("reference")

      await(newJob.execute())

      theCasesUpdated.daysElapsed shouldBe 0
    }

    "Update Days Elapsed - for case created one working day ago" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-02T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenThereAreNoEventsFor("reference")

      await(newJob.execute())

      theCasesUpdated.daysElapsed shouldBe 1
    }

    "Update Days Elapsed - for case created multiple working days ago" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-04T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenThereAreNoEventsFor("reference")

      await(newJob.execute())

      theCasesUpdated.daysElapsed shouldBe 3
    }

    "Update Days Elapsed - excluding weekends" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-07T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-05T00:00:00"))
      givenThereAreNoEventsFor("reference")

      await(newJob.execute())

      theCasesUpdated.daysElapsed shouldBe 0
    }

    "Update Days Elapsed - excluding bank holidays" in {
      givenABankHolidayOn("2019-01-01")
      givenTodaysDateIs("2019-01-02T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenThereAreNoEventsFor("reference")

      await(newJob.execute())

      theCasesUpdated.daysElapsed shouldBe 0
    }

    "Update Days Elapsed - excluding referred days" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-04T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenAPageOfEventsFor("reference", 1, 1, aStatusChangeWith(date = "2019-01-01T00:00:00", status = CaseStatus.REFERRED))

      await(newJob.execute())

      theCasesUpdated.daysElapsed shouldBe 0
    }

    "Update Days Elapsed - excluding multiple referred days" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-04T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenAPageOfEventsFor("reference", 1, 1,
        aStatusChangeWith(date = "2019-01-01T00:00:00", status = CaseStatus.REFERRED),
        aStatusChangeWith(date = "2019-01-02T00:00:00", status = CaseStatus.OPEN),
        aStatusChangeWith(date = "2019-01-03T00:00:00", status = CaseStatus.REFERRED)
      )

      await(newJob.execute())

      theCasesUpdated.daysElapsed shouldBe 1
    }

    "Update Days Elapsed - excluding multiple referred events on the same day" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-04T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenAPageOfEventsFor("reference", 1, 1,
        aStatusChangeWith(date = "2019-01-02T00:00:00", status = CaseStatus.REFERRED),
        aStatusChangeWith(date = "2019-01-02T12:00:00", status = CaseStatus.OPEN)
      )

      await(newJob.execute())

      theCasesUpdated.daysElapsed shouldBe 2
    }

    "Update Days Elapsed - excluding suspended days" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-04T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenAPageOfEventsFor("reference", 1, 1, aStatusChangeWith(date = "2019-01-01T00:00:00", status = CaseStatus.SUSPENDED))

      await(newJob.execute())

      theCasesUpdated.daysElapsed shouldBe 0
    }

    "Update Days Elapsed - excluding multiple suspended days" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-04T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenAPageOfEventsFor("reference", 1, 1,
        aStatusChangeWith(date = "2019-01-01T00:00:00", status = CaseStatus.SUSPENDED),
        aStatusChangeWith(date = "2019-01-02T00:00:00", status = CaseStatus.OPEN),
        aStatusChangeWith(date = "2019-01-03T00:00:00", status = CaseStatus.SUSPENDED)
      )

      await(newJob.execute())

      theCasesUpdated.daysElapsed shouldBe 1
    }

    "Update Days Elapsed - excluding multiple suspended events on the same day" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-04T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenAPageOfEventsFor("reference", 1, 1,
        aStatusChangeWith(date = "2019-01-02T00:00:00", status = CaseStatus.SUSPENDED),
        aStatusChangeWith(date = "2019-01-02T12:00:00", status = CaseStatus.OPEN)
      )

      await(newJob.execute())

      theCasesUpdated.daysElapsed shouldBe 2
    }

    "Update Days Elapsed - for multiple cases" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-01T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 2, 2,
        aCaseWith(reference = "reference-1", createdDate = "2019-01-01T00:00:00"),
        aCaseWith(reference = "reference-2", createdDate = "2019-01-02T00:00:00")
      )
      givenThereAreNoEventsFor("reference-1")
      givenThereAreNoEventsFor("reference-2")

      await(newJob.execute())

      verify(caseService, times(2)).update(any[Case], refEq(false))
    }

    "Update Days Elapsed - for multiple pages of cases" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-01T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 2, aCaseWith(reference = "reference-1", createdDate = "2019-01-01T00:00:00"))
      givenAPageOfCases(2, 1, 2, aCaseWith(reference = "reference-2", createdDate = "2019-01-02T00:00:00"))
      givenThereAreNoEventsFor("reference-1")
      givenThereAreNoEventsFor("reference-2")

      await(newJob.execute())

      verify(caseService, times(2)).update(any[Case], refEq(false))
    }

    "Update Days Elapsed - for a migrated case extracted today with zero migrated days elapsed" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2020-07-03T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1,
        aMigratedCaseWith(
          reference = "reference",
          createdDate = "2020-07-01T00:00:00",
          dateOfExtract = "2020-07-03T00:00:00",
          migratedDaysElapsed = 0L))
      givenThereAreNoEventsFor("reference")

      await(newJob.execute())

      theCasesUpdated.daysElapsed shouldBe 0L
    }

    "Update Days Elapsed - for a migrated case extracted today with a non-zero migrated days elapsed" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2020-07-03T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1,
        aMigratedCaseWith(
          reference = "reference",
          createdDate = "2020-07-01T00:00:00",
          dateOfExtract = "2020-07-03T00:00:00",
          migratedDaysElapsed = 2L))
      givenThereAreNoEventsFor("reference")

      await(newJob.execute())

      theCasesUpdated.daysElapsed shouldBe 2L
    }

    "Update Days Elapsed - for a migrated case extracted 2 days ago with a zero migrated days elapsed" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2020-07-03T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1,
        aMigratedCaseWith(
          reference = "reference",
          createdDate = "2020-06-29T00:00:00",
          dateOfExtract = "2020-07-01T00:00:00",
          migratedDaysElapsed = 0L))
      givenThereAreNoEventsFor("reference")

      await(newJob.execute())

      theCasesUpdated.daysElapsed shouldBe 2L
    }

    "Update Days Elapsed - for a migrated case extracted 2 days ago with a non-zero migrated days elapsed" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2020-07-03T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1,
        aMigratedCaseWith(
          reference = "reference",
          createdDate = "2020-06-29T00:00:00",
          dateOfExtract = "2020-07-01T00:00:00",
          migratedDaysElapsed = 2L))
      givenThereAreNoEventsFor("reference")

      await(newJob.execute())

      theCasesUpdated.daysElapsed shouldBe 4L
    }

    "Update Days Elapsed - for a migrated referred case" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2020-07-03T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1,
        aMigratedCaseWith(
          reference = "reference",
          createdDate = "2020-06-29T00:00:00",
          dateOfExtract = "2020-07-01T00:00:00",
          migratedDaysElapsed = 1L))
      givenAPageOfEventsFor("reference", 1, 1,
        aStatusChangeWith(date = "2020-06-30T00:00:00", status = CaseStatus.REFERRED),
      )

      await(newJob.execute())

      theCasesUpdated.daysElapsed shouldBe 1L
    }
  }

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

  private val caseStatusChangeEventTypes = Set(EventType.CASE_STATUS_CHANGE, EventType.CASE_REFERRAL, EventType.CASE_COMPLETED, EventType.CASE_CANCELLATION)

  private def givenAPageOfEventsFor(reference: String, page: Int, totalEvents: Int, events: Event*): Unit = {
    val pagination = Pagination(page = page, pageSize = Integer.MAX_VALUE)
    given(eventService.search(EventSearch(Some(Set(reference)), Some(caseStatusChangeEventTypes)), pagination)) willReturn
      Future.successful(Paged(events, pagination, totalEvents))
  }

  private def givenThereAreNoEventsFor(reference: String): Unit = {
    val pagination = Pagination(pageSize = Integer.MAX_VALUE)
    given(eventService.search(EventSearch(Some(Set(reference)), Some(caseStatusChangeEventTypes)), pagination)) willReturn
      Future.successful(Paged.empty[Event])
  }

  private def aCaseWith(reference: String, createdDate: String): Case = CaseData.createCase().copy(
    reference = reference,
    createdDate = LocalDateTime.parse(createdDate).atZone(ZoneOffset.UTC).toInstant,
    daysElapsed = 0
  )

  private def aMigratedCaseWith(reference: String, createdDate: String, dateOfExtract: String, migratedDaysElapsed: Long): Case =
    aCaseWith(reference, createdDate).copy(
      dateOfExtract = Some(LocalDateTime.parse(dateOfExtract).atZone(ZoneOffset.UTC).toInstant),
      migratedDaysElapsed = Some(migratedDaysElapsed)
    )

  private def aStatusChangeWith(date: String, status: CaseStatus): Event = {
    val e = mock[Event]
    given(e.details) willReturn createCaseStatusChangeEventDetails(mock[CaseStatus], status)
    given(e.timestamp) willReturn LocalDateTime.parse(date).toInstant(ZoneOffset.UTC)
    e
  }

  private def newJob: ActiveDaysElapsedJob = new ActiveDaysElapsedJob(appConfig, caseService, eventService, bankHolidaysConnector)

  private def givenABankHolidayOn(date: String*): Unit = {
    when(bankHolidaysConnector.get()(any[HeaderCarrier])).thenReturn(date.map(LocalDate.parse).toSet)
  }

  private def givenNoBankHolidays(): Unit = {
    when(bankHolidaysConnector.get()(any[HeaderCarrier])).thenReturn(Set.empty[LocalDate])
  }

  private def givenTodaysDateIs(date: String): Unit = {
    val zone: ZoneId = ZoneOffset.UTC
    val instant = LocalDateTime.parse(date).atZone(zone).toInstant
    given(appConfig.clock).willReturn(Clock.fixed(instant, zone))
  }

  private def givenUpdatingACaseReturnsItself(): Unit = {
    given(caseService.update(any[Case], any[Boolean])).will(new Answer[Future[Option[Case]]] {
      override def answer(invocation: InvocationOnMock): Future[Option[Case]] = Future.successful(Option(invocation.getArgument[Case](0)))
    })
  }

}
