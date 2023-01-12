/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.quartz.CronExpression
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.config.{AppConfig, JobConfig}
import uk.gov.hmrc.bindingtariffclassification.connector.BankHolidaysConnector
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.{CaseService, EventService}
import uk.gov.hmrc.bindingtariffclassification.sort.CaseSortField
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.LockRepository
import util.CaseData
import util.EventData.createCaseStatusChangeEventDetails

import java.time._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.successful

// scalastyle:off magic.number
class ReferredDaysElapsedJobTest extends BaseSpec with BeforeAndAfterEach {

  private val caseService           = mock[CaseService]
  private val eventService          = mock[EventService]
  private val bankHolidaysConnector = mock[BankHolidaysConnector]
  private val fixedDate             = ZonedDateTime.of(2021, 2, 15, 12, 0, 0, 0, ZoneOffset.UTC)
  private val clock                 = Clock.fixed(fixedDate.toInstant, ZoneOffset.UTC)
  private val appConfig             = mock[AppConfig]
  private val lockRepo              = mock[LockRepository]

  private val cronExpr = new CronExpression("0 0 14 * * ?")

  private val jobConfig =
    JobConfig(name = "ReferredDaysElapsed", enabled = true, schedule = cronExpr)

  private val caseSearch = CaseSearch(
    filter = CaseFilter(statuses = Some(Set(PseudoCaseStatus.REFERRED, PseudoCaseStatus.SUSPENDED))),
    sort   = Some(CaseSort(Set(CaseSortField.REFERENCE)))
  )

  override def afterEach(): Unit = {
    super.afterEach()
    reset(appConfig, caseService, eventService)
  }

  override protected def beforeEach(): Unit = {
    given(appConfig.clock).willReturn(clock)
    given(appConfig.referredDaysElapsed).willReturn(jobConfig)
    given(lockRepo.takeLock(any[String], any[String], any[scala.concurrent.duration.Duration]))
      .willReturn(Future.successful(true))
  }

  "Scheduled Job" should {

    "Configure 'Name'" in {
      newJob.name shouldBe "ReferredDaysElapsed"
    }

    "Configure 'enabled'" in {
      newJob.enabled shouldBe true
    }

    "Configure 'nextRunTime'" in {
      newJob.schedule shouldBe cronExpr
    }
  }

  "Scheduled Job 'Execute'" should {

    "Update Referred Days Elapsed - for no cases" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-01T00:00:00")

      givenAPageOfCases(1, 1, 0)

      await(newJob.execute())
    }

    "Update Referred Days Elapsed - for case referred today" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-01T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenAPageOfEventsFor(
        "reference",
        1,
        1,
        aStatusChangeWith(date = "2019-01-01T00:00:00", status = CaseStatus.REFERRED)
      )

      await(newJob.execute())

      theCasesUpdated.referredDaysElapsed shouldBe 0
    }

    "Update Referred Days Elapsed - for case created one working day ago" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-02T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenAPageOfEventsFor(
        "reference",
        1,
        1,
        aStatusChangeWith(date = "2019-01-01T00:00:00", status = CaseStatus.REFERRED)
      )

      await(newJob.execute())

      theCasesUpdated.referredDaysElapsed shouldBe 1
    }

    "Update Referred Days Elapsed - for case created multiple working days ago" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-04T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenAPageOfEventsFor(
        "reference",
        1,
        1,
        aStatusChangeWith(date = "2019-01-01T00:00:00", status = CaseStatus.REFERRED)
      )

      await(newJob.execute())

      theCasesUpdated.referredDaysElapsed shouldBe 3
    }

    "Update Referred Days Elapsed - excluding weekends" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-07T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-05T00:00:00"))
      givenAPageOfEventsFor(
        "reference",
        1,
        1,
        aStatusChangeWith(date = "2019-01-05T00:00:00", status = CaseStatus.REFERRED)
      )

      await(newJob.execute())

      theCasesUpdated.referredDaysElapsed shouldBe 0
    }

    "Update Referred Days Elapsed - excluding bank holidays" in {
      givenABankHolidayOn("2019-01-01")
      givenTodaysDateIs("2019-01-02T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenAPageOfEventsFor(
        "reference",
        1,
        1,
        aStatusChangeWith(date = "2019-01-01T00:00:00", status = CaseStatus.REFERRED)
      )

      await(newJob.execute())

      theCasesUpdated.referredDaysElapsed shouldBe 0
    }

    "Update Referred Days Elapsed - no valid referral event" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-04T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference                  = "reference", createdDate      = "2019-01-01T00:00:00"))
      givenAPageOfEventsFor("reference", 1, 1, aStatusChangeWith(date = "2019-01-01T00:00:00", status = CaseStatus.NEW))

      await(newJob.execute())

      theCasesUpdated.referredDaysElapsed shouldBe 0
    }

    "Update Referred Days Elapsed - most recent event is not a referral event" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-04T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenAPageOfEventsFor(
        "reference",
        1,
        1,
        aStatusChangeWith(date = "2019-01-01T00:00:00", status = CaseStatus.OPEN),
        aStatusChangeWith(date = "2019-01-02T00:00:00", status = CaseStatus.REFERRED),
        aStatusChangeWith(date = "2019-01-03T00:00:00", status = CaseStatus.OPEN)
      )

      await(newJob.execute())

      theCasesUpdated.referredDaysElapsed shouldBe 0
    }

    "Update Referred Days Elapsed - excluding non-referred events on the same day" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-05T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenAPageOfEventsFor(
        "reference",
        1,
        1,
        aStatusChangeWith(date = "2019-01-02T00:00:00", status = CaseStatus.OPEN),
        aStatusChangeWith(date = "2019-01-02T12:00:00", status = CaseStatus.REFERRED)
      )

      await(newJob.execute())

      theCasesUpdated.referredDaysElapsed shouldBe 3
    }

    "Update Referred Days Elapsed - use the latest referral period" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-05T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenAPageOfEventsFor(
        "reference",
        1,
        1,
        aStatusChangeWith(date = "2019-01-01T00:00:00", status = CaseStatus.OPEN),
        aStatusChangeWith(date = "2019-01-02T00:00:00", status = CaseStatus.REFERRED),
        aStatusChangeWith(date = "2019-01-03T00:00:00", status = CaseStatus.OPEN),
        aStatusChangeWith(date = "2019-01-04T00:00:00", status = CaseStatus.REFERRED)
      )

      await(newJob.execute())

      theCasesUpdated.referredDaysElapsed shouldBe 1
    }

    "Update Referred Days Elapsed - suspended case" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-05T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(1, 1, 1, aCaseWith(reference = "reference", createdDate = "2019-01-01T00:00:00"))
      givenAPageOfEventsFor(
        "reference",
        1,
        1,
        aStatusChangeWith(date = "2019-01-01T00:00:00", status = CaseStatus.OPEN),
        aStatusChangeWith(date = "2019-01-02T00:00:00", status = CaseStatus.SUSPENDED)
      )

      await(newJob.execute())

      theCasesUpdated.referredDaysElapsed shouldBe 3
    }

    "Update Referred Days Elapsed - for multiple cases" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-01T00:00:00")

      givenUpdatingACaseReturnsItself()
      givenAPageOfCases(
        1,
        2,
        2,
        aCaseWith(reference = "reference-1", createdDate = "2019-01-01T00:00:00"),
        aCaseWith(reference = "reference-2", createdDate = "2019-01-02T00:00:00")
      )
      givenThereAreNoEventsFor("reference-1")
      givenThereAreNoEventsFor("reference-2")

      await(newJob.execute())

      verify(caseService, times(2)).update(any[Case], refEq(false))
    }

    "Update Referred Days Elapsed - for multiple pages of cases" in {
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

  private def givenAPageOfEventsFor(reference: String, page: Int, totalEvents: Int, events: Event*): Unit = {
    val pagination = Pagination(page = page, pageSize = Integer.MAX_VALUE)
    given(
      eventService.search(
        EventSearch(Some(Set(reference)), Some(Set(EventType.CASE_STATUS_CHANGE, EventType.CASE_REFERRAL))),
        pagination
      )
    ) willReturn
      Future.successful(Paged(events, pagination, totalEvents))
  }

  private def givenThereAreNoEventsFor(reference: String): Unit = {
    val pagination = Pagination(pageSize = Integer.MAX_VALUE)
    given(
      eventService.search(
        EventSearch(Some(Set(reference)), Some(Set(EventType.CASE_STATUS_CHANGE, EventType.CASE_REFERRAL))),
        pagination
      )
    ) willReturn
      Future.successful(Paged.empty[Event])
  }

  private def aCaseWith(reference: String, createdDate: String): Case =
    CaseData
      .createCase()
      .copy(
        reference           = reference,
        createdDate         = LocalDateTime.parse(createdDate).atZone(ZoneOffset.UTC).toInstant,
        referredDaysElapsed = 0
      )

  private def aStatusChangeWith(date: String, status: CaseStatus): Event = {
    val e = mock[Event]
    given(e.details) willReturn createCaseStatusChangeEventDetails(mock[CaseStatus], status)
    given(e.timestamp) willReturn LocalDateTime.parse(date).toInstant(ZoneOffset.UTC)
    e
  }

  private def newJob: ReferredDaysElapsedJob =
    new ReferredDaysElapsedJob(caseService, eventService, bankHolidaysConnector, lockRepo, appConfig)

  private def givenABankHolidayOn(date: String*): Unit =
    when(bankHolidaysConnector.get()(any[HeaderCarrier])).thenReturn(successful(date.map(LocalDate.parse).toSet))

  private def givenNoBankHolidays(): Unit =
    when(bankHolidaysConnector.get()(any[HeaderCarrier])).thenReturn(successful(Set.empty[LocalDate]))

  private def givenTodaysDateIs(date: String): Unit = {
    val zone: ZoneId = ZoneOffset.UTC
    val instant      = LocalDateTime.parse(date).atZone(zone).toInstant
    given(appConfig.clock).willReturn(Clock.fixed(instant, zone))
  }

  private def givenUpdatingACaseReturnsItself(): Unit =
    given(caseService.update(any[Case], any[Boolean])).will((invocation: InvocationOnMock) =>
      Future.successful(Option(invocation.getArgument[Case](0)))
    )

}
