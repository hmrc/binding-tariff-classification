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
import java.time.temporal.ChronoUnit

import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.connector.BankHolidaysConnector
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.{CaseService, EventService}
import uk.gov.hmrc.bindingtariffclassification.sort.CaseSortField
import uk.gov.hmrc.bindingtariffclassification.utils.DateUtil._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future._
import scala.concurrent.duration._

@Singleton
class ActiveDaysElapsedJob @Inject()(
                                      appConfig: AppConfig,
                                      caseService: CaseService,
                                      eventService: EventService,
                                      bankHolidaysConnector: BankHolidaysConnector
                                    ) extends ScheduledJob {

  private implicit val config: AppConfig = appConfig
  private implicit val carrier: HeaderCarrier = HeaderCarrier()
  private lazy val jobConfig = appConfig.activeDaysElapsed
  private lazy val criteria = CaseSearch(
    filter = CaseFilter(statuses = Some(Set(PseudoCaseStatus.OPEN, PseudoCaseStatus.NEW))),
    sort = Some(CaseSort(Set(CaseSortField.REFERENCE)))
  )

  override val name: String = "ActiveDaysElapsed"

  override def interval: FiniteDuration = jobConfig.interval

  override def firstRunTime: LocalTime = jobConfig.elapseTime

  override def execute(): Future[Unit] = for {
    bankHolidays <- bankHolidaysConnector.get()
    _ <- process(1)(bankHolidays)
  } yield ()

  private def process(page: Int)(implicit bankHolidays: Set[LocalDate]): Future[Unit] = {
    caseService.get(criteria, Pagination(page = page)) flatMap { pager =>
      sequence(pager.results.map(refreshDaysElapsed)).map(_ => pager)
    } flatMap {
      case pager if pager.hasNextPage => process(page + 1)
      case _ => successful(())
    }
  }

  private def getTrackingStartDate(c: Case): LocalDate = {
    if (c.dateOfExtract.isDefined) {
      // For migrated cases, we only track the case history after the date of extraction
      LocalDateTime.ofInstant(c.dateOfExtract.get, ZoneOffset.UTC).toLocalDate
    } else {
      if (c.application.isLiabilityOrder) {
        val liability = c.application.asLiabilityOrder
        if (liability.dateOfReceipt.isDefined) {
          LocalDateTime.ofInstant(liability.dateOfReceipt.get, ZoneOffset.UTC).toLocalDate
        } else {
          //when liability doesn't have date of receipt use case create date
          LocalDateTime.ofInstant(c.createdDate, ZoneOffset.UTC).toLocalDate
        }
      } else {
        LocalDateTime.ofInstant(c.createdDate, ZoneOffset.UTC).toLocalDate
      }
    }
  }

  private def refreshDaysElapsed(c: Case)(implicit bankHolidays: Set[LocalDate]): Future[Unit] = {
    lazy val trackingStartDate: LocalDate = getTrackingStartDate(c)
    val daysTracked: Long = ChronoUnit.DAYS.between(trackingStartDate, LocalDate.now(appConfig.clock))

    // Working days between when the case was tracked and Now
    val workingDaysTracked: Seq[Instant] = (0L until daysTracked)
      .map(trackingStartDate.plusDays)
      .filterNot(bankHoliday)
      .filterNot(weekend)
      .map(toInstant)

    val caseStatusChangeEventTypes = Set(EventType.CASE_STATUS_CHANGE, EventType.CASE_REFERRAL, EventType.CASE_COMPLETED, EventType.CASE_CANCELLATION)

    for {
      // Get the Status Change events for that case
      events <- eventService.search(EventSearch(Some(Set(c.reference)), Some(caseStatusChangeEventTypes)), Pagination(1, Integer.MAX_VALUE))

      // Generate a timeline of the Case Status over time
      statusTimeline: StatusTimeline = StatusTimeline.from(events.results)

      // Filter down to the days the case was not Referred or Suspended
      trackedActionableDays: Seq[Instant] = workingDaysTracked
        .filterNot(statusTimeline.statusOn(_).contains(CaseStatus.REFERRED))
        .filterNot(statusTimeline.statusOn(_).contains(CaseStatus.SUSPENDED))

      trackedDaysElapsed = trackedActionableDays.length.toLong
      untrackedDaysElapsed = c.migratedDaysElapsed.getOrElse(0L)

      totalDaysElapsed = trackedDaysElapsed + untrackedDaysElapsed

      // Update the case
      updatedCase <- caseService.update(c.copy(daysElapsed = totalDaysElapsed), upsert = false)
      _ = logResult(c, updatedCase)
    } yield ()
  }

  private def logResult(original: Case, updated: Option[Case]): Unit = {
    updated match {
      case Some(c) if original.daysElapsed != c.daysElapsed =>
        Logger.info(s"$name: Updated Days Elapsed of Case [${original.reference}] from [${original.daysElapsed}] to [${c.daysElapsed}]")
      case None =>
        Logger.warn(s"$name: Failed to update Days Elapsed of Case [${original.reference}]")
      case _ =>
        ()
    }
  }
}
