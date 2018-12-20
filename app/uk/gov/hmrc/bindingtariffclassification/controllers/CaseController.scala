/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.CaseService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class CaseController @Inject()(appConfig: AppConfig,
                               caseService: CaseService,
                               caseParamsMapper: CaseParamsMapper,
                               caseSortMapper: CaseSortMapper) extends CommonController {

  import JsonFormatters.{formatCase, formatNewCase}

  lazy private val deleteModeFilter = DeleteMode.actionFilter(appConfig)

  def deleteAll(): Action[AnyContent] = deleteModeFilter.async { implicit request =>
    caseService.deleteAll map (_ => NoContent) recover recovery
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
        caseService.update(caseRequest) map {
          case None => NotFound(JsErrorResponse(ErrorCode.NOT_FOUND, "Case not found"))
          case Some(c: Case) => Ok(Json.toJson(c))
        }
      } else Future.successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Invalid case reference")))
    } recover recovery
  }

  def get(queue_id: Option[String],
          assignee_id: Option[String],
          status: Option[String],
          sort_by: Option[String],
          sort_direction: Option[String]): Action[AnyContent] = Action.async { implicit request =>
    caseService.get(
      caseParamsMapper.from(queue_id, assignee_id, status),
      caseSortMapper.from(sort_by, sort_direction)
    ) map { cases =>
      Ok(Json.toJson(cases))
    } recover recovery
  }

  def getByReference(reference: String): Action[AnyContent] = Action.async { implicit request =>
    caseService.getByReference(reference) map {
      case None => NotFound(JsErrorResponse(ErrorCode.NOT_FOUND, "Case not found"))
      case Some(c: Case) => Ok(Json.toJson(c))
    } recover recovery
  }

}
