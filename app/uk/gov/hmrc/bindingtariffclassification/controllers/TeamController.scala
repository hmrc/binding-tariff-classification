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

import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.ErrorCode.NOTFOUND
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.TeamService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful


@Singleton
class TeamController @Inject() (
  appConfig: AppConfig,
  teamService: TeamService,
  parser: BodyParsers.Default,
  mcc: MessagesControllerComponents
) extends CommonController(mcc) {

  def create: Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[NewTeamRequest] { teamRequest: NewTeamRequest =>
      for {
        t <- teamService.insert(teamRequest.team)
      } yield Created(Json.toJson(t)(RESTFormatters.formatTeam))
    } recover recovery map { result =>
      logger.debug(s"Team creation Result : $result");
      result
    }
  }
}