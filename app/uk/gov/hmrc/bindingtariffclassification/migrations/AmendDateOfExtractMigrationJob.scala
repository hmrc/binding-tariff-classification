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

package uk.gov.hmrc.bindingtariffclassification.migrations

import java.time.{Instant, LocalDate, ZoneOffset}
import javax.inject.{Inject, Singleton}

import uk.gov.hmrc.bindingtariffclassification.common.Logging
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.bindingtariffclassification.sort.CaseSortField

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{sequence, successful}

@Singleton
class AmendDateOfExtractMigrationJob @Inject() (
  caseService: CaseService
) extends MigrationJob
    with Logging {
  private lazy val criteria = CaseSearch(
    filter = CaseFilter(migrated = Some(true)),
    sort   = Some(CaseSort(Set(CaseSortField.REFERENCE)))
  )
  private lazy val originalDate = LocalDate.of(2021, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant
  private lazy val updatedDate  = LocalDate.of(2020, 12, 31).atStartOfDay(ZoneOffset.UTC).toInstant

  override val name: String = "AmendDateOfExtract"

  override def execute(): Future[Unit] =
    for {
      _ <- process(page = 1, fromDate = originalDate, toDate = updatedDate)
    } yield ()

  override def rollback(): Future[Unit] =
    for {
      _ <- process(page = 1, fromDate = updatedDate, toDate = originalDate)
    } yield ()

  private def process(page: Int, fromDate: Instant, toDate: Instant): Future[Unit] =
    caseService.get(criteria, Pagination(page = page)) flatMap { pager =>
      sequence(pager.results.map(amendDateOfExtract(_, fromDate, toDate))).map(_ => pager)
    } flatMap {
      case pager if pager.hasNextPage => process(page = page + 1, fromDate = fromDate, toDate = toDate)
      case _                          => successful(())
    }

  private def amendDateOfExtract(c: Case, fromDate: Instant, toDate: Instant): Future[Unit] =
    if (c.dateOfExtract.contains(fromDate)) {
      val updatedCase = c.copy(dateOfExtract = Some(toDate))
      for {
        updatedCase <- caseService.update(updatedCase, upsert = false)
        _ = logResult(c, updatedCase)
      } yield ()
    } else {
      successful(())
    }

  private def logResult(original: Case, updated: Option[Case]): Unit =
    updated match {
      case Some(c) if original.dateOfExtract != c.dateOfExtract =>
        logger.info(
          s"Updated DateOfExtract of Case [${original.reference}] from [${original.dateOfExtract}] to [${c.dateOfExtract}]"
        )
      case None =>
        logger.warn(s"Failed to update DateOfExtract of Case [${original.reference}]")
      case _ =>
        ()
    }
}
