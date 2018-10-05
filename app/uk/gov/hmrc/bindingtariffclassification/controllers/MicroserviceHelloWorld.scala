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
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.service.{CaseService, EventService}
import uk.gov.hmrc.bindingtariffclassification.utils.RandomNumberGenerator
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

@Singleton()
class MicroserviceHelloWorld @Inject()(caseService: CaseService, eventService: EventService) extends BaseController {


  def hello(): Action[AnyContent] = Action.async { implicit request =>

    lazy val execution = request.headers.toMap.get(LOCATION) match {
      case Some(Seq(_: String)) => Future.successful(Ok("{}"))
      case _ => Future.successful(BadRequest("{}"))
    }

    val delay = FiniteDuration(RandomNumberGenerator.next(), MILLISECONDS)


    Logger.debug(s"Execution delay: $delay")

    createCaseData()
    createEventData()

    akka.pattern.after(duration = delay, using = Play.current.actorSystem.scheduler)(execution)
  }


  private def createEventData(): Unit = {

    // INSERT
    val e1 = Event("event_1", Note(Some("hey Note")), "user_1", "REF_1234")
    val r1 = Await.result(eventService.upsert(e1), 2.seconds)
    Logger.debug(s"Event JSON document inserted? $r1")

    val e2 = Event("event_2", CaseStatusChange(from=CaseStatus.DRAFT, to=CaseStatus.NEW), "user_1", "REF_1234")
    val r2 = Await.result(eventService.upsert(e2), 2.seconds)
    Logger.debug(s"Event JSON document inserted? $r2")

    val e3 = Event(RandomNumberGenerator.next().toString, Attachment(url="URL", mimeType = "media/jpg"), "user_2", "REF_1234")
    val r3 = Await.result(eventService.upsert(e3), 2.seconds)
    Logger.debug(s"Event JSON document inserted? $r3")

    // GET BY REF
    val readEvent1 = Await.result(eventService.getOne("event_1"), 2.seconds)
    Logger.debug(s"$readEvent1")
  }

  private def createCaseData(): Unit = {

    // INSERT
    val c1 = Case(
      "REF_1234",
      CaseStatus.DRAFT,
      assigneeId = Some(RandomNumberGenerator.next().toString),
      application = BTIApplication(holder = EORIDetails("field1", "field1", "field1", "field1", "field1", "field1", "field1"), goodsDescription = "Hello Man!")
    )
    val r1 = Await.result(caseService.upsert(c1), 2.seconds)
    Logger.debug(s"Case JSON document inserted? $r1")

    val c2 = Case(
      "REF_Offline",
      CaseStatus.DRAFT,
      assigneeId = Some(RandomNumberGenerator.next().toString),
      application = BTIOfflineApplication(holder = EORIDetails("f2", "f2", "f2", "f2", "f2", "f2", "f2"), goodsDescription = "Hello Boy!")
    )
    val r2 = Await.result(caseService.upsert(c2), 2.seconds)
    Logger.debug(s"Case JSON document inserted? $r2")

    val c3 = Case(
      "REf_liability",
      CaseStatus.DRAFT,
      assigneeId = Some(RandomNumberGenerator.next().toString),
      application = LiabilityOrder(entryNumber = "entryNumber")
    )
    val r3 = Await.result(caseService.upsert(c3), 2.seconds)
    Logger.debug(s"Case JSON document inserted? $r3")

    // GET BY REF
    val readCase1 = Await.result(caseService.getOne("REF_1234"), 2.seconds)
    Logger.debug(s"$readCase1")

    val readCase2 = Await.result(caseService.getOne("REF_Offline"), 2.seconds)
    Logger.debug(s"$readCase2")

    val readCase3 = Await.result(caseService.getOne("REf_liability"), 2.seconds)
    Logger.debug(s"$readCase3")

    // UPDATE
    val r1u = Await.result(caseService.upsert(c1.copy(application = c2.application)), 2.seconds)
    Logger.debug(s"Case JSON document inserted? $r1u")

    val r2u = Await.result(caseService.upsert(c2.copy(application = c1.application)), 2.seconds)
    Logger.debug(s"Case JSON document inserted? $r2u")
  }

}
