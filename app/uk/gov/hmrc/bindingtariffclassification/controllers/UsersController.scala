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
import uk.gov.hmrc.bindingtariffclassification.model.{Operator, _}
import uk.gov.hmrc.bindingtariffclassification.service.UsersService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

@Singleton
class UsersController @Inject() (appConfig: AppConfig, usersService: UsersService, mcc: MessagesControllerComponents)
    extends CommonController(mcc) {

  def fetchUserDetails(id: String): Action[AnyContent] = Action.async {
    usersService.getUserById(id) map handleNotFound recover recovery
  }

  def allUsers(search: UserSearch, pagination: Pagination): Action[AnyContent] =
    Action.async {
      usersService.search(search, pagination) map { users: Paged[Operator] =>
        Ok(Json.toJson(users))
      } recover recovery
    }

  def createUser: Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[NewUserRequest] { userRequest: NewUserRequest =>
      for {
        c <- usersService.insertUser(userRequest.operator)
      } yield Created(Json.toJson(c)(RESTFormatters.formatOperator))
    } recover recovery map { result =>
      logger.debug(s"User creation Result : $result");
      result
    }
  }

  def updateUser(id: String): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withJsonBody[Operator] { user: Operator =>
        if (user.id == id) {
          val upsert = request.headers.get(USER_AGENT) match {
            case Some(agent) => appConfig.upsertAgents.contains(agent)
            case _           => false
          }
          usersService.updateUser(user, upsert) map handleNotFound recover recovery
        } else
          successful(
            BadRequest(
              JsErrorResponse(
                ErrorCode.INVALID_REQUEST_PAYLOAD,
                "Invalid user id"
              )
            )
          )
      } recover recovery
    }

  def markDeleted(id: String): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withJsonBody[Operator] { user: Operator =>
        if (user.id == id) {
          val upsert = request.headers.get(USER_AGENT) match {
            case Some(agent) => appConfig.upsertAgents.contains(agent)
            case _           => false
          }
          usersService.updateUser(user.copy(deleted = true), upsert) map handleNotFound recover recovery
        } else {
          successful(
            BadRequest(
              JsErrorResponse(
                ErrorCode.INVALID_REQUEST_PAYLOAD,
                "Invalid user id"
              )
            )
          )
        }
      } recover recovery
    }

  private[controllers] def handleNotFound: PartialFunction[Option[Operator], Result] = {
    case Some(user: Operator) => Ok(Json.toJson(user))
    case _                    => NotFound(JsErrorResponse(NOTFOUND, "User not found"))
  }
}
