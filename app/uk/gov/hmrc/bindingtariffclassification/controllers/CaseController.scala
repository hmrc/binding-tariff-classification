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

import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.ErrorCode.NOTFOUND
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.CaseService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

@Singleton
class CaseController @Inject() (
  appConfig: AppConfig,
  caseService: CaseService,
  parser: BodyParsers.Default,
  mcc: MessagesControllerComponents
) extends CommonController(mcc) {

  lazy private val testModeFilter = TestMode.actionFilter(appConfig, parser)

  def deleteAll(): Action[AnyContent] = testModeFilter.async {
    caseService.deleteAll() map (_ => NoContent) recover recovery
  }

  def delete(reference: String): Action[AnyContent] = testModeFilter.async {
    caseService.delete(reference) map (_ => NoContent) recover recovery
  }

  def create: Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[NewCaseRequest] { caseRequest: NewCaseRequest =>
      for {
        r <- caseService.nextCaseReference(caseRequest.application.`type`)
        c <- caseService.insert(caseRequest.toCase(r))
        _ <- caseService.addInitialSampleStatusIfExists(c)
      } yield Created(Json.toJson(c)(RESTFormatters.formatCase))
    } recover recovery map { result =>
      logger.debug(s"Case creation Result : $result");
      result
    }
  }

  def put(reference: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[Case] { caseRequest: Case =>
      if (caseRequest.reference == reference) {
        val upsert = request.headers.get(USER_AGENT) match {
          case Some(agent) => appConfig.upsertAgents.contains(agent)
          case _           => false
        }
        caseService.update(caseRequest, upsert) map handleNotFound recover recovery
      } else successful(BadRequest(JsErrorResponse(ErrorCode.INVALID_REQUEST_PAYLOAD, "Invalid case reference")))
    } recover recovery
  }

  def update(reference: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[CaseUpdate] { caseUpdate =>
      caseService
        .update(reference, caseUpdate)
        .map(handleNotFound)
    }.recover(recovery)
  }

  private[controllers] def handleNotFound: PartialFunction[Option[Case], Result] = {
    case Some(c: Case) => Ok(Json.toJson(c))
    case _             => NotFound(JsErrorResponse(NOTFOUND, "Case not found"))
  }

  def get(search: CaseSearch, pagination: Pagination): Action[AnyContent] = Action.async {
    caseService.get(search, pagination) map { cases =>
      Ok(Json.toJson(cases))
    } recover recovery
  }

  def getByReference(reference: String): Action[AnyContent] = Action.async {
    caseService.getByReference(reference) map handleNotFound recover recovery
  }

}
