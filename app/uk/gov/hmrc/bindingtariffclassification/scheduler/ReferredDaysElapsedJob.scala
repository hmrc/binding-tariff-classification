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
class ReferredDaysElapsedJob @Inject()(appConfig: AppConfig,
                                       caseService: CaseService,
                                       eventService: EventService,
                                       bankHolidaysConnector: BankHolidaysConnector) extends ScheduledJob {

  private implicit val config: AppConfig = appConfig
  private implicit val carrier: HeaderCarrier = HeaderCarrier()
  private lazy val jobConfig = appConfig.referredDaysElapsed
  private lazy val criteria = CaseSearch(
    filter = CaseFilter(statuses = Some(Set(PseudoCaseStatus.REFERRED))),
    sort = Some(CaseSort(Set(CaseSortField.REFERENCE)))
  )

  override val name: String = "ReferredDaysElapsed"

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
    val daysSinceCreated: Long = ChronoUnit.DAYS.between(createdDate, LocalDate.now(appConfig.clock))

    // Working days between the created date and Now
    val workingDays: Seq[Instant] = (0L until daysSinceCreated)
      .map(createdDate.plusDays)
      .filterNot(bankHoliday)
      .filterNot(weekend)
      .map(toInstant)

    for {
      // Get the Status Change events for that case
      events <- eventService.search(EventSearch(Some(Set(c.reference)), Some(Set(EventType.CASE_STATUS_CHANGE))), Pagination(1, Integer.MAX_VALUE))

      // Generate a timeline of the Case Status over time
      statusTimeline: StatusTimeline = StatusTimeline.from(events.results)

      // Filter down to the days the case was Referred
      referredDays: Seq[Instant] = workingDays
        .filter(statusTimeline.statusOn(_).contains(CaseStatus.REFERRED))

      // Update the case
      _ <- caseService.update(c.copy(referredDaysElapsed = referredDays.size), upsert = false)

      _ = Logger.info(s"$name: Updated Days Elapsed of Case [${c.reference}] from [${c.daysElapsed}] to [${referredDays.size}]")
    } yield ()
  }

}
