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
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.bindingtariffclassification.model.{Application, Case, Decision, JsonFormatters}
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global

@Singleton()
class CaseController @Inject()(caseService: CaseService) extends BaseController {

  import JsonFormatters._

  def createCase(): Action[JsValue] = Action.async(parse.json) { implicit request =>

    withJsonBody[Case] { payload =>

      caseService.save(payload) map {
        case (true, response) => Ok(Json.toJson(response))
        case (false, _) => BadRequest

      }
    } recover recovery
  }

  def handleException(e: Throwable): Result = {
    Logger.logger.info(e.getMessage)
    InternalServerError(e.getMessage)
  }

  def recovery: PartialFunction[Throwable, Result] = {
    case e => handleException(e)
  }

}
