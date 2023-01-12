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

import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.bindingtariffclassification.controllers.actions.AuthAction
import uk.gov.hmrc.bindingtariffclassification.service.BtaCardService

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class BtaCardController @Inject() (
  btaCardService: BtaCardService,
  authAction: AuthAction,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends CommonController(mcc) {

  def getBtaCard: Action[AnyContent] = authAction.async { implicit request =>
    logger.info(s"[BtaCardController][getBtaCard] Count request received")
    btaCardService
      .generateBtaCard(request.eori)
      .map { card =>
        logger.info(s"[BtaCardController][getBtaCard] Card generated: ${Json.toJson(card).toString()}")
        Ok(Json.toJson(card))
      }
      .recover {
        case ex: Exception =>
          logger.error(s"[BtaCardController][getBtaCard] Failure generating BTA Card counts: ${ex.getMessage}")
          InternalServerError
      }
  }
}
