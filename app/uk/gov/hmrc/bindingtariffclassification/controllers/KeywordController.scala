/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.Inject
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.ErrorCode.NOTFOUND
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.KeywordService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

class KeywordController @Inject() (
  appConfig: AppConfig,
  keywordService: KeywordService,
  mcc: MessagesControllerComponents
) extends CommonController(mcc) {

  def addKeyword(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[NewKeywordRequest] { keywordRequest: NewKeywordRequest =>
      for {
        k <- keywordService.addKeyword(keywordRequest.keyword)
      } yield Created(Json.toJson(k)(RESTFormatters.formatKeyword))
    } recover recovery map { result =>
      logger.debug(s"Keyword added with result : $result")
      result
    }
  }

  def deleteKeyword(name: String): Action[AnyContent] = Action.async {
    keywordService.deleteKeyword(name) map (_ => NoContent) recover recovery
  }

  def approveKeyword(name: String): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withJsonBody[Keyword] { keyword: Keyword =>
        if (keyword.name == name) {
          val upsert = request.headers.get(USER_AGENT) match {
            case Some(agent) => appConfig.upsertAgents.contains(agent)
            case _           => false
          }
          keywordService.approveKeyword(keyword, upsert) map handleNotFound recover recovery
        } else {
          successful(
            BadRequest(
              JsErrorResponse(
                ErrorCode.INVALID_REQUEST_PAYLOAD,
                "Invalid keyword name"
              )
            )
          )
        }
      } recover recovery
    }

  def getAllKeywords(pagination: Pagination): Action[AnyContent] =
    Action.async {
      keywordService.findAll(pagination).map { allKeywords =>
        Ok(Json.toJson(allKeywords))
      } recover recovery
    }

  def fetchCaseKeywords(pagination: Pagination): Action[AnyContent] =
    Action.async {
      keywordService.fetchCaseKeywords(pagination).map { keywords =>
        Ok(Json.toJson(keywords))
      } recover recovery
    }

  private[controllers] def handleNotFound: PartialFunction[Option[Keyword], Result] = {
    case Some(keyword: Keyword) => Ok(Json.toJson(keyword))
    case _                      => NotFound(JsErrorResponse(NOTFOUND, "Keyword not found"))
  }
}
