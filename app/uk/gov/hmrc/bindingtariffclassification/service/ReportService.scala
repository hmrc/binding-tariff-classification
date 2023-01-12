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

package uk.gov.hmrc.bindingtariffclassification.service

import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.reporting._
import uk.gov.hmrc.bindingtariffclassification.repository.CaseRepository

import javax.inject.Inject
import scala.concurrent.Future

class ReportService @Inject() (caseRepository: CaseRepository) {

  def summaryReport(report: SummaryReport, pagination: Pagination): Future[Paged[ResultGroup]] =
    caseRepository.summaryReport(report, pagination)

  def caseReport(report: CaseReport, pagination: Pagination): Future[Paged[Map[String, ReportResultField[_]]]] =
    caseRepository.caseReport(report, pagination)

  def queueReport(report: QueueReport, pagination: Pagination): Future[Paged[QueueResultGroup]] =
    caseRepository.queueReport(report, pagination)
}
