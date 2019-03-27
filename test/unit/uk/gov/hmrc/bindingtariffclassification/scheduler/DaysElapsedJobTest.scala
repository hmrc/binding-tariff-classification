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

package uk.gov.hmrc.bindingtariffclassification.scheduler

import java.time._

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.config.{AppConfig, JobConfig}
import uk.gov.hmrc.bindingtariffclassification.connector.BankHolidaysConnector
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.{CaseService, EventService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future
import scala.concurrent.duration._

class DaysElapsedJobTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val caseService = mock[CaseService]
  private val eventService = mock[EventService]
  private val bankHolidaysConnector = mock[BankHolidaysConnector]
  private val appConfig = mock[AppConfig]

  private val caseSearch = CaseSearch(Filter(statuses = Some(Set(CaseStatus.OPEN, CaseStatus.NEW))))

  override def afterEach(): Unit = {
    super.afterEach()
    reset(appConfig, caseService)
  }

  "Scheduled Job" should {

    "Configure 'Name'" in {
      newJob.name shouldBe "DaysElapsed"
    }

    "Configure 'firstRunTime'" in {
      val runTime = LocalTime.of(14, 0)
      given(appConfig.daysElapsed).willReturn(JobConfig(runTime, 1.day))

      newJob.firstRunTime shouldBe runTime
    }

    "Configure 'interval'" in {
      given(appConfig.daysElapsed).willReturn(JobConfig(LocalTime.MIDNIGHT, 1.day))

      newJob.interval shouldBe 1.day
    }

  }

  "Scheduled Job 'Execute'" should {
    given(appConfig.daysElapsed).willReturn(JobConfig(LocalTime.MIDNIGHT, 1.day))

    "Update Days Elapsed - for case created today" in {
      givenNoBankHolidays()
      givenTodaysDateIs("2019-01-01T00:00:00")

      givenAPageOfCases(1, 1, aCaseWithReferenceAndCreatedDate("reference", "2019-01-01T00:00:00"))
      givenAPageOfEventsFor("reference", 1, 1)

      await(newJob.execute())

      theCasesUpdated().daysElapsed  shouldBe 0
    }
  }

  private def theCasesUpdated(index: Int = 0): Case = {
    val captor: ArgumentCaptor[Case] = ArgumentCaptor.forClass(classOf[Case])
    verify(caseService).update(captor.capture(), upsert = false)
    captor.getAllValues.get(index)
  }

  private def givenAPageOfCases(page: Int, totalCases: Int, cases: Case*): Unit = {
    val pagination = Pagination(page = page)
    given(caseService.get(caseSearch, pagination)) willReturn Future.successful(Paged(cases, pagination, totalCases))
  }

  private def givenAPageOfEventsFor(reference: String, page: Int, totalEvents: Int, events: Event*): Unit = {
    val pagination = Pagination(page = page, pageSize = Integer.MAX_VALUE)
    given(eventService.search(EventSearch(reference, Some(EventType.CASE_STATUS_CHANGE)), pagination)) willReturn Future.successful(Paged(events, pagination, totalEvents))
  }

  private def givenThereAreNoEventsFor(reference: String): Unit = {
    val pagination = Pagination(pageSize = Integer.MAX_VALUE)
    given(eventService.search(EventSearch(reference, Some(EventType.CASE_STATUS_CHANGE)), pagination)) willReturn Future.successful(Paged.empty[Event])
  }

  private def aCaseWithReferenceAndCreatedDate(ref: String, date: String): Case = {
    val c = mock[Case]
    given(c.reference) willReturn ref
    given(c.createdDate) willReturn LocalDateTime.parse(date).atZone(ZoneOffset.UTC).toInstant
    given(c.daysElapsed) willReturn 0
    c
  }

  private def aStatusChange(date: String, status: CaseStatus): Event = {
    val e = mock[Event]
    given(e.details) willReturn CaseStatusChange(
      mock[CaseStatus],
      status
    )
    given(e.timestamp) willReturn LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant
    e
  }

  private def newJob: DaysElapsedJob = new DaysElapsedJob(appConfig, caseService, eventService, bankHolidaysConnector)

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

}
