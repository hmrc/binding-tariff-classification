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
import uk.gov.hmrc.bindingtariffclassification.model.{Case, JsonFormatters}
import uk.gov.hmrc.bindingtariffclassification.service.CaseService

import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global

@Singleton()
class CaseController @Inject()(caseService: CaseService) extends CommonController {

  import JsonFormatters._

  def createCase(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    Logger.warn(s"request = ${request.body} ")
    withJsonBody[Case] { c: Case =>
      caseService.save(c) map {
        case (true, response) => Created(Json.toJson(response))
        case (false, _) => Conflict
        // TODO: the JSON case is now already updated in mongo, so it is too late :-)
        // We probably want to have 2 methods in `CaseService`:
        // - one for update (it should error with 404 if you try to update a non-existing case)
        // - one for create (it should error with 400 or something else if you try to create a case with the same `reference` of an existing case)
        // Have a look at the `findAndModify` atomic utility in Mongo
      }
    } recover recovery
  }

  def handleException(e: Throwable): Result = {
    // TODO: we need to distinguish between
    // - a JSON parsing error (400 Bad Request) - for example if the request is not a valid JSON case
    // - a system error (500 Internal Server Error) - for example if the database is not running
    Logger.logger.info(e.getMessage)
    InternalServerError(e.getMessage)
  }

  def recovery: PartialFunction[Throwable, Result] = {
    case e => handleException(e)
  }

}
