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
import play.api.mvc._
import uk.gov.hmrc.bindingtariffclassification.model.{Case, ErrorCode, JsErrorResponse, JsonFormatters}
import uk.gov.hmrc.bindingtariffclassification.service.CaseService

import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global

@Singleton()
class CaseController @Inject()(caseService: CaseService) extends CommonController {

  import JsonFormatters._

  def create: Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[Case] { caseRequest: Case =>
      caseService.insert(caseRequest) map { c => Created(Json.toJson(c)) }
    } recover recovery
  }

  def update(reference: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[Case] { caseRequest: Case =>
      // TODO: require (reference == caseRequest.reference)
      // TODO (if `caseRequest` does not contain the reference): val updatedCase = caseRequest.copy(reference = reference)
      caseService.update(caseRequest) map {
        case None => NotFound(JsErrorResponse(ErrorCode.NOT_FOUND, "Case not found"))
        case Some(c: Case) => Ok(Json.toJson(c))
      }
    } recover recovery
  }

  def getAll: Action[AnyContent] = Action.async { implicit request =>
    caseService.getAll map (cases => Ok(Json.toJson(cases))) recover recovery
  }

  def getByReference(reference: String): Action[AnyContent] = Action.async { implicit request =>
    caseService.getByReference(reference) map {
      case None => NotFound(JsErrorResponse(ErrorCode.NOT_FOUND, "Case not found"))
      case Some(c: Case) => Ok(Json.toJson(c))
    } recover recovery
  }
}