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

package uk.gov.hmrc.bindingtariffclassification.controllers

import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.bindingtariffclassification.model.Pagination
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters._
import uk.gov.hmrc.bindingtariffclassification.model.reporting._
import uk.gov.hmrc.bindingtariffclassification.service.ReportService

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class ReportingController @Inject() (
  reportService: ReportService,
  mcc: MessagesControllerComponents
) extends CommonController(mcc) {

  def summaryReport(report: SummaryReport, pagination: Pagination): Action[AnyContent] = Action.async {
    reportService.summaryReport(report, pagination).map(result => Ok(Json.toJson(result)))
  }

  def caseReport(report: CaseReport, pagination: Pagination): Action[AnyContent] = Action.async {
    reportService.caseReport(report, pagination).map(result => Ok(Json.toJson(result)))
  }

  def queueReport(report: QueueReport, pagination: Pagination): Action[AnyContent] = Action.async {
    reportService.queueReport(report, pagination).map(result => Ok(Json.toJson(result)))
  }
}
