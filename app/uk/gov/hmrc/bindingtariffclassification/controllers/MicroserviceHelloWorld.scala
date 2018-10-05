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

import java.time.ZonedDateTime

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import play.api.{Logger, Play}
import uk.gov.hmrc.bindingtariffclassification.model.LiabilityOrderType.LiabilityOrderType
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
    val e1 = Event(RandomNumberGenerator.next().toString, Note(Some("hey Note")), "user_1", "REF_1234")
    val r1 = Await.result(eventService.upsert(e1), 2.seconds)
    Logger.debug(s"Event JSON document inserted? $r1")

    val e2 = Event(RandomNumberGenerator.next().toString, CaseStatusChange(from = CaseStatus.DRAFT, to = CaseStatus.NEW), "user_1", "REF_1234")
    val r2 = Await.result(eventService.upsert(e2), 2.seconds)
    Logger.debug(s"Event JSON document inserted? $r2")

    val e3 = Event(RandomNumberGenerator.next().toString, Attachment(url = "URL", mimeType = "media/jpg"), "user_2", "REF_1234")
    val r3 = Await.result(eventService.upsert(e3), 2.seconds)
    Logger.debug(s"Event JSON document inserted? $r3")

    // GET BY REF
    val readEvent1 = Await.result(eventService.getOne(e1.id), 2.seconds)
    Logger.debug(s"$readEvent1")
  }

  private def createCaseData(): Unit = {

    // INSERT
    val c1 = createCase("1", createBTI("1"))
    val r1 = Await.result(caseService.upsert(c1), 2.seconds)
    Logger.debug(s"Case JSON document inserted? $r1")

    val c2 = createCase("2", createOfflineBTI("2"))
    val r2 = Await.result(caseService.upsert(c2), 2.seconds)
    Logger.debug(s"Case JSON document inserted? $r2")

    val c3 = createCase("3", createLaibility("3"))
    val r3 = Await.result(caseService.upsert(c3), 2.seconds)
    Logger.debug(s"Case JSON document inserted? $r3")

    // GET BY REF
    val readCase1 = Await.result(caseService.getOne(c1.reference), 2.seconds)
    Logger.debug(s"$readCase1")

    val readCase2 = Await.result(caseService.getOne(c2.reference), 2.seconds)
    Logger.debug(s"$readCase2")

    val readCase3 = Await.result(caseService.getOne(c3.reference), 2.seconds)
    Logger.debug(s"$readCase3")

    // UPDATE
    val r1u = Await.result(caseService.upsert(c1.copy(application = c2.application)), 2.seconds)
    Logger.debug(s"Case JSON document inserted? $r1u")

    val r2u = Await.result(caseService.upsert(c2.copy(application = c1.application)), 2.seconds)
    Logger.debug(s"Case JSON document inserted? $r2u")
  }


  def createCase(id: String, app: Application): Case = {
    Case(
      s"Ref_$id",
      CaseStatus.DRAFT,
      assigneeId = Some(RandomNumberGenerator.next().toString),
      application = app
    )
  }

  def createBTI(id: String) = {
    BTIApplication(
      holder = createEORIDetails(s"holder_$id"),
      contact = Contact("", "", ""),
      agent = createEORIDetails(s"agent_$id"),
      "",
      "",
      "",
      "",
      "",
      "",
      "",
      true,
      true,
      false)
  }

  def createOfflineBTI(id: String) = {
    BTIOfflineApplication(
      holder = createEORIDetails(s"holder_$id"),
      contact = Contact("", "", ""),
      agent = createEORIDetails(s"agent_$id"),
      "",
      "",
      "",
      "",
      "",
      "",
      "",
      true,
      true,
      false)
  }

  def createLaibility(id: String) = {
    LiabilityOrder(
      holder = createEORIDetails(s"holder_$id"),
      contact = Contact("", "", ""),
      LiabilityOrderType.LIVE,
      s"port_$id",
      s"entruyNumber_$id",
      ZonedDateTime.now()
    )
  }


  def createEORIDetails(prefix: String): EORIDetails = {
    EORIDetails(s"eori_$prefix", s"tradername_$prefix", s"addressLine1_$prefix", s"addressLine2_$prefix", s"addressLine3_$prefix", s"postcode_$prefix", s"country_$prefix")
  }


}
