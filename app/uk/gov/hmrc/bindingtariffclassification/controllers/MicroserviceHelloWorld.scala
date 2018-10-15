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
import play.api.mvc._
import play.api.{Logger, Play}
import uk.gov.hmrc.bindingtariffclassification.service.{CaseService, EventService}
import uk.gov.hmrc.bindingtariffclassification.utils.RandomGenerator
import uk.gov.hmrc.bindingtariffclassification.todelete.CaseData._
import uk.gov.hmrc.bindingtariffclassification.todelete.EventData._
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

@Singleton()
class MicroserviceHelloWorld @Inject()(caseService: CaseService, eventService: EventService) extends BaseController {

  def hello(): Action[AnyContent] = Action.async { implicit request =>

    lazy val execution: Future[Result] = request.headers.toMap.get(LOCATION) match {
      case Some(Seq(loc: String)) =>
        Logger.debug(s"You are located in $loc")
        val reference = createCaseData()
        createEventData(reference)
        Future.successful(Ok("{}"))
      case _ => Future.successful(BadRequest("{}"))
    }

    val delay = FiniteDuration(RandomGenerator.nextDelay(), MILLISECONDS)
    Logger.debug(s"Execution delay: $delay")

    akka.pattern.after(duration = delay, using = Play.current.actorSystem.scheduler)(execution)
  }

  private def createEventData(caseReference: String): Unit = {

    // INSERT
    val e1 = createNoteEvent(caseReference)
    val r1 = Await.result(eventService.insert(e1), 2.seconds)
    Logger.debug(s"Note Event document inserted? $r1")

    val e2 = createCaseStatusChangeEvent(caseReference)
    val r2 = Await.result(eventService.insert(e2), 2.seconds)
    Logger.debug(s"Case-Status-Change Event document inserted? $r2")

    // GET BY ID
    val e1r = Await.result(eventService.getById(e1.id), 2.seconds)
    Logger.debug(s"$e1r")

    // GET BY CASE REFERENCE
    val events = Await.result(eventService.getByCaseReference(e1.caseReference), 2.seconds)
    Logger.debug(s"$events")

//     INSERT DUPLICATED record - failing
//    Await.result(eventService.insert(e3), 2.seconds)
  }

  private def createCaseData(): String = {

    // INSERT
    val c1 = createCase(createBTIApplication)
    val r1 = Await.result(caseService.insert(c1), 2.seconds)
    Logger.debug(s"BTI document inserted? $r1")

    val c3 = createCase(createLiabilityOrder)
    val r3 = Await.result(caseService.insert(c3), 2.seconds)
    Logger.debug(s"Liability Order document inserted? $r3")

    // GET BY REF
    val rc1 = Await.result(caseService.getByReference(c1.reference), 2.seconds)
    Logger.debug(s"$rc1")

    val rc3 = Await.result(caseService.getByReference(c3.reference), 2.seconds)
    Logger.debug(s"$rc3")

    // UPDATE
    val r1u = Await.result(caseService.insert(c1.copy(application = c3.application)), 2.seconds)
    Logger.debug(s"Case JSON document inserted? $r1u")

    val r2u = Await.result(caseService.insert(c3.copy(application = c1.application)), 2.seconds)
    Logger.debug(s"Case JSON document inserted? $r2u")

    c1.reference
  }

}
