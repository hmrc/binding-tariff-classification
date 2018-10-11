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

import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{Request, Result}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import uk.gov.hmrc.play.bootstrap.controller.BaseController

trait CommonController extends BaseController {

  override protected def withJsonBody[T]
  (f: T => Future[Result])(implicit request: Request[JsValue], m: Manifest[T], reads: Reads[T]): Future[Result] = {
    Logger.warn(s"log for auditing the overridden method. request.body: ${request.body.toString()}")
    Try(request.body.validate[T]) match {
      case Success(JsSuccess(payload, _)) => f(payload)
      case Success(JsError(errs)) => Future.successful(handleUnprocessableEntity(errs.seq.toString()))
      case Failure(e) => Future.successful(handleServerError(e.getMessage))
    }
  }

  private def handleUnprocessableEntity(message: String): Result = {
    UnprocessableEntity(JsErrorResponse("payload.not.valid", message))
  }

  private def handleServerError(message: String): Result = {
    InternalServerError(JsErrorResponse("unexpected.error", message))
  }

  object JsErrorResponse {
    def apply(errorCode: String, message: String): JsObject =
      Json.obj(
        "code" -> errorCode,
        "message" -> message
      )
  }

}