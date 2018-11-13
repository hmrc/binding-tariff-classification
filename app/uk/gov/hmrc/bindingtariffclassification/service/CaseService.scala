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

package uk.gov.hmrc.bindingtariffclassification.service

import java.util.UUID

import javax.inject._
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.search.CaseParamsFilter
import uk.gov.hmrc.bindingtariffclassification.model.{Case, CaseStatusChange, Event, NewCase}
import uk.gov.hmrc.bindingtariffclassification.repository.{CaseRepository, SequenceRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class CaseService @Inject()(caseRepository: CaseRepository, sequenceRepository: SequenceRepository, eventService: EventService) {

  def insert(c: NewCase): Future[Case] = {
    newCaseReference.flatMap(ref => caseRepository.insert(c.toCase(ref)))
  }

  private def createEvent(originalCase: Case, newStatus: CaseStatus): Future[Event] = {
    val changeStatusEvent = Event(
      id = UUID.randomUUID().toString,
      details = CaseStatusChange(from = originalCase.status, to = newStatus),
      userId = "0", // this should be the currently loggedIn user. See DIT-311
      caseReference = originalCase.reference)

    eventService.insert(changeStatusEvent)
  }

  def updateStatus(reference: String, status: CaseStatus): Future[Option[(Case, Case)]] = {

    // TODO: use OptionT ?
    // TODO: use for-comprehension ?
    caseRepository.updateStatus(reference, status).flatMap {
      case None => Future.successful(None)
      case Some(original: Case) =>
        createEvent(original, status).map { _ => Some((original, original.copy(status = status))) }
    }

  }

  def update(c: Case): Future[Option[Case]] = {
    caseRepository.update(c)
  }

  def getByReference(reference: String): Future[Option[Case]] = {
    caseRepository.getByReference(reference)
  }

  def get(searchBy: CaseParamsFilter, sortBy: Option[String]): Future[Seq[Case]] = {
    caseRepository.get(searchBy, sortBy)
  }

  private def newCaseReference: Future[String] = {
    sequenceRepository.incrementAndGetByName("case")
      .map(_.value.toString)
  }

}
