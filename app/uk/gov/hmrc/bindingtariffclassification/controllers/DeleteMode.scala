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

import play.api.mvc._
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.{ErrorCode, JsErrorResponse}

import scala.concurrent.Future

object DeleteMode {

  def actionFilter(appConfig: AppConfig) = new ActionBuilder[Request] with ActionFilter[Request] {

    override protected def filter[A](request: Request[A]): Future[Option[Result]] = Future.successful {

      implicit val r: Request[A] = request

      if (appConfig.isDeleteEnabled) None
      else Some(play.api.mvc.Results.Forbidden(JsErrorResponse(ErrorCode.FORBIDDEN, "You are not allowed to delete.")))

    }

  }

}
