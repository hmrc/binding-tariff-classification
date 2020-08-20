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
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
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
    filter = CaseFilter(statuses = Some(Set(PseudoCaseStatus.REFERRED, PseudoCaseStatus.SUSPENDED))),
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
      sequence(pager.results.map(refreshReferredDaysElapsed)).map(_ => pager)
    } flatMap {
      case pager if pager.hasNextPage => process(page + 1)
      case _ => successful(())
    }
  }

  private def getReferralStartDate(c: Case): Future[Option[LocalDate]] =
    for {
      eventSearch <- eventService.search(
        search = EventSearch(Some(Set(c.reference)), Some(Set(EventType.CASE_STATUS_CHANGE, EventType.CASE_REFERRAL))),
        pagination = Pagination(1, Integer.MAX_VALUE))

      startTimestamp = eventSearch.results
        .filter(_.details.isInstanceOf[FieldChange[CaseStatus]])
        .sortBy(_.timestamp)(Ordering[Instant].reverse)
        .headOption
        .filter(event => Set(CaseStatus.REFERRED, CaseStatus.SUSPENDED).contains(event.details.asInstanceOf[FieldChange[CaseStatus]].to))
        .map(_.timestamp)

    } yield startTimestamp.map(LocalDateTime.ofInstant(_, ZoneOffset.UTC).toLocalDate)

  private def getReferredDaysElapsed(startDate: LocalDate)(implicit bankHolidays: Set[LocalDate]): Long = {
    val daysOnReferral = ChronoUnit.DAYS.between(startDate, LocalDate.now(appConfig.clock))

    val referredDaysElapsed = (0L until daysOnReferral)
      .map(startDate.plusDays)
      .filterNot(bankHoliday)
      .filterNot(weekend)
      .length

    referredDaysElapsed
  }

  private def refreshReferredDaysElapsed(c: Case)(implicit bankHolidays: Set[LocalDate]): Future[Unit] = {
    for {
      referralStartDate <- getReferralStartDate(c)

      referredDaysElapsed = referralStartDate
        .map(getReferredDaysElapsed)
        .getOrElse {
          Logger.warn(s"$name: Unable to find referral event for [${c.reference}]")
          0L
        }

      // Update the case
      updatedCase <- caseService.update(c.copy(referredDaysElapsed = referredDaysElapsed), upsert = false)
      _ = logResult(c, updatedCase)
    } yield ()
  }

  private def logResult(original: Case, updated: Option[Case]): Unit = {
    updated match {
      case Some(c) if original.referredDaysElapsed != c.referredDaysElapsed =>
        Logger.info(s"$name: Updated Referred Days Elapsed of Case [${original.reference}] from [${original.referredDaysElapsed}] to [${c.referredDaysElapsed}]")
      case None =>
        Logger.warn(s"$name: Failed to update Referred Days Elapsed of Case [${original.reference}]")
      case _ =>
        ()
    }
  }

}
