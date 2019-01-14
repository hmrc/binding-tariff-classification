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

package uk.gov.hmrc.bindingtariffclassification.controllers

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.ErrorCode.NOTFOUND
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.CaseService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

@Singleton
class CaseController @Inject()(appConfig: AppConfig,
                               caseService: CaseService,
                               caseParamsMapper: CaseParamsMapper,
                               caseSortMapper: CaseSortMapper) extends CommonController {

  import RESTFormatters.{formatCase, formatNewCase}

  lazy private val testModeFilter = TestMode.actionFilter(appConfig)

  def deleteAll(): Action[AnyContent] = testModeFilter.async { implicit request =>
    caseService.deleteAll() map (_ => NoContent) recover recovery
  }

  def create: Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[NewCaseRequest] { caseRequest: NewCaseRequest =>
      for {
        r <- caseService.nextCaseReference
        c <- caseService.insert(caseRequest.toCase(r))
      } yield Created(Json.toJson(c))
    } recover recovery
  }

  def update(reference: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[Case] { caseRequest: Case =>
      if (caseRequest.reference == reference) {
        val upsert = request.headers.get("User-Agent") match {
          case Some(agent) => appConfig.upsertAgents.contains(agent)
          case _ => false
        }
        caseService.update(caseRequest, upsert) map handleNotFound recover recovery
      } else successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Invalid case reference")))
    } recover recovery
  }

  def get(queue_id: Option[String],
          assignee_id: Option[String],
          status: Option[String],
          sort_by: Option[String],
          sort_direction: Option[String]): Action[AnyContent] = Action.async { implicit request =>
    caseService.get(
      searchBy = caseParamsMapper.from(queue_id, assignee_id, status),
      sortBy = caseSortMapper.from(sort_by, sort_direction)
    ) map { cases => Ok(Json.toJson(cases)) } recover recovery
  }

  def getByReference(reference: String): Action[AnyContent] = Action.async { implicit request =>
    caseService.getByReference(reference) map handleNotFound recover recovery
  }

  private[controllers] def handleNotFound: PartialFunction[Option[Case], Result] = {
    case Some(c: Case) => Ok(Json.toJson(c))
    case _ => NotFound(JsErrorResponse(NOTFOUND, "Case not found"))
  }

}
