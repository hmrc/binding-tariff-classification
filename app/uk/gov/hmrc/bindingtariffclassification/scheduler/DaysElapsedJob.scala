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
import java.time.temporal.ChronoUnit

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.connector.BankHolidaysConnector
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.{CaseService, EventService}
import uk.gov.hmrc.bindingtariffclassification.sort.CaseSortField
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.immutable.SortedMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future._
import scala.concurrent.duration._

@Singleton
class DaysElapsedJob @Inject()(appConfig: AppConfig,
                               caseService: CaseService,
                               eventService: EventService,
                               bankHolidaysConnector: BankHolidaysConnector) extends ScheduledJob {

  private implicit val carrier: HeaderCarrier = HeaderCarrier()
  private lazy val jobConfig = appConfig.daysElapsed
  private lazy val criteria = CaseSearch(
    filter = Filter(statuses = Some(Set(CaseStatus.OPEN, CaseStatus.NEW))),
    sort = Some(CaseSort(CaseSortField.REFERENCE))
  )

  override val name: String = "DaysElapsed"

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

  private def refreshDaysElapsed(c: Case)(implicit bankHolidays: Set[LocalDate]): Future[Unit] = {
    val createdDate: LocalDate = LocalDateTime.ofInstant(c.createdDate, ZoneOffset.UTC).toLocalDate
    val daysSinceCreated: Int = ChronoUnit.DAYS.between(createdDate, LocalDate.now(appConfig.clock)).toInt

    // Working days between the created date and Noe
    val workingDays: Seq[Instant] = (0 until daysSinceCreated)
      .map(createdDate.plusDays(_))
      .filterNot(bankHoliday)
      .filterNot(weekend)
      .map(toInstant)

    for {
      // Get the Status Change events for that case
      events <- eventService.search(EventSearch(c.reference, Some(EventType.CASE_STATUS_CHANGE)), Pagination(1, Integer.MAX_VALUE))

      // Generate a timeline of the Case Status over time
      statusTimeline: StatusTimeline = new StatusTimeline(
        events.results
          .filter(_.details.isInstanceOf[CaseStatusChange])
          .map { event =>
            (event.timestamp, event.details.asInstanceOf[CaseStatusChange].to)
          }
      )

      // Filter down to the days the case was not Referred
      actionableDays: Seq[Instant] = workingDays.filterNot(statusTimeline.statusOn(_).contains(CaseStatus.REFERRED))

      // Update the case
      _ <- caseService.update(c.copy(daysElapsed = actionableDays.size), upsert = false)
    } yield ()
  }

  private def toInstant: LocalDate => Instant = _.atStartOfDay(appConfig.clock.getZone).toInstant

  private def bankHoliday(date: LocalDate)(implicit bankHolidays: Set[LocalDate]): Boolean = bankHolidays.contains(date)

  private def weekend(date: LocalDate): Boolean = Set(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).contains(date.getDayOfWeek)

  private class StatusTimeline(statusChanges: Seq[(Instant, CaseStatus)]) {
    lazy val timeline: SortedMap[Instant, CaseStatus] = SortedMap[Instant, CaseStatus](statusChanges: _*)

    def statusOn(date: Instant): Option[CaseStatus] = {
      if (timeline.contains(date)) {
        timeline.get(date)
      } else {
        timeline.until(date).lastOption.map(_._2)
      }
    }
  }

}
