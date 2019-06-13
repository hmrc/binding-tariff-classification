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

package uk.gov.hmrc.bindingtariffclassification.service

import java.time.Instant

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.BDDMockito._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.reporting._
import uk.gov.hmrc.bindingtariffclassification.repository.{CaseRepository, EventRepository}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class ReportServiceTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val caseRepository = mock[CaseRepository]
  private val eventRepository = mock[EventRepository]
  private val service = new ReportService(caseRepository, eventRepository)

  override def afterEach(): Unit = {
    super.afterEach()
    reset(caseRepository, eventRepository)
  }

  "Service 'Get Report'" should {
    "Delegate to Case Repository for simple report" in {
      val report = CaseReport(
        filter = CaseReportFilter(),
        group = Set(CaseReportGroup.QUEUE),
        field = CaseReportField.ACTIVE_DAYS_ELAPSED
      )

      given(caseRepository.generateReport(report)) willReturn Future.successful(Seq.empty[ReportResult])

      await(service.generate(report)) shouldBe Seq.empty
    }
  }

  "Service 'Get Report' with Referred Date Filter" should {

    "Append Case References of status changes to referred" in {
      val events = Seq(statusChange("ref2", CaseStatus.OPEN, CaseStatus.REFERRED))
      given(eventRepository.search(any[EventSearch], any[Pagination])) willReturn Future.successful(Paged(events))
      given(caseRepository.generateReport(any[CaseReport])) willReturn Future.successful(Seq.empty[ReportResult])

      val report = CaseReport(
        filter = CaseReportFilter(
          reference = Some(Set("ref1")),
          referralDate = Some(InstantRange(
            min = Instant.MIN,
            max = Instant.MAX
          ))
        ),
        group = Set(CaseReportGroup.QUEUE),
        field = CaseReportField.ACTIVE_DAYS_ELAPSED
      )

      await(service.generate(report)) shouldBe Seq.empty

      theEventSearch shouldBe EventSearch(
        `type` = Some(Set(EventType.CASE_STATUS_CHANGE)),
        timestampMin = Some(Instant.MIN),
        timestampMax = Some(Instant.MAX)
      )

      theReportGenerated shouldBe CaseReport(
        filter = CaseReportFilter(reference = Some(Set("ref1", "ref2"))),
        group = Set(CaseReportGroup.QUEUE),
        field = CaseReportField.ACTIVE_DAYS_ELAPSED
      )
    }

    "Append Case References of status changes from referred" in {
      val events = Seq(statusChange("ref2", CaseStatus.REFERRED, CaseStatus.OPEN))
      given(eventRepository.search(any[EventSearch], any[Pagination])) willReturn Future.successful(Paged(events))
      given(caseRepository.generateReport(any[CaseReport])) willReturn Future.successful(Seq.empty[ReportResult])

      val report = CaseReport(
        filter = CaseReportFilter(
          reference = Some(Set("ref1")),
          referralDate = Some(InstantRange(
            min = Instant.MIN,
            max = Instant.MAX
          ))
        ),
        group = Set(CaseReportGroup.QUEUE),
        field = CaseReportField.ACTIVE_DAYS_ELAPSED
      )

      await(service.generate(report)) shouldBe Seq.empty

      theEventSearch shouldBe EventSearch(
        `type` = Some(Set(EventType.CASE_STATUS_CHANGE)),
        timestampMin = Some(Instant.MIN),
        timestampMax = Some(Instant.MAX)
      )

      theReportGenerated shouldBe CaseReport(
        filter = CaseReportFilter(reference = Some(Set("ref1", "ref2"))),
        group = Set(CaseReportGroup.QUEUE),
        field = CaseReportField.ACTIVE_DAYS_ELAPSED
      )
    }

    "Not append case references of non-referral events" in {
      val events = Seq(statusChange("ref2", CaseStatus.NEW, CaseStatus.OPEN))
      given(eventRepository.search(any[EventSearch], any[Pagination])) willReturn Future.successful(Paged(events))
      given(caseRepository.generateReport(any[CaseReport])) willReturn Future.successful(Seq.empty[ReportResult])

      val report = CaseReport(
        filter = CaseReportFilter(
          reference = Some(Set("ref1")),
          referralDate = Some(InstantRange(
            min = Instant.MIN,
            max = Instant.MAX
          ))
        ),
        group = Set(CaseReportGroup.QUEUE),
        field = CaseReportField.ACTIVE_DAYS_ELAPSED
      )

      await(service.generate(report)) shouldBe Seq.empty

      theEventSearch shouldBe EventSearch(
        `type` = Some(Set(EventType.CASE_STATUS_CHANGE)),
        timestampMin = Some(Instant.MIN),
        timestampMax = Some(Instant.MAX)
      )

      theReportGenerated shouldBe CaseReport(
        filter = CaseReportFilter(reference = Some(Set("ref1"))),
        group = Set(CaseReportGroup.QUEUE),
        field = CaseReportField.ACTIVE_DAYS_ELAPSED
      )
    }
  }

  def statusChange(reference: String, from: CaseStatus, to: CaseStatus) = Event(
    details = CaseStatusChange(
      from = from,
      to = to
    ),
    operator = mock[Operator],
    caseReference = reference,
    timestamp = Instant.EPOCH
  )

  def theEventSearch: EventSearch = {
    val captor = ArgumentCaptor.forClass(classOf[EventSearch])
    verify(eventRepository).search(captor.capture(), any[Pagination])
    captor.getValue
  }

  def theReportGenerated: CaseReport = {
    val captor = ArgumentCaptor.forClass(classOf[CaseReport])
    verify(caseRepository).generateReport(captor.capture())
    captor.getValue
  }

}
