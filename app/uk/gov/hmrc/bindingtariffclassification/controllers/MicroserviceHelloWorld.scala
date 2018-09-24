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

import javax.inject.Singleton
import play.api.{Logger, Play}
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import play.api.mvc._
import uk.gov.hmrc.bindingtariffclassification.utils.RandomNumberGenerator

import scala.concurrent.duration._
import scala.concurrent.Future

@Singleton()
class MicroserviceHelloWorld extends BaseController {

  /*
      Test Slack integration

   */

  def hello(): Action[AnyContent] = Action.async { implicit request =>

    lazy val execution = request.headers.toMap.get(LOCATION) match {
      case Some(Seq(_: String)) => Future.successful(Ok("{}"))
      case _ => Future.successful(InternalServerError("{}"))
    }

    val delay = FiniteDuration(RandomNumberGenerator.next(), MILLISECONDS)


    Logger.debug(s"Execution delay: $delay")
    akka.pattern.after(duration = delay, using = Play.current.actorSystem.scheduler)(execution)
  }

}
