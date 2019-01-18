/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject._
import uk.gov.hmrc.bindingtariffclassification.crypto.Crypto
import uk.gov.hmrc.bindingtariffclassification.model.Case
import uk.gov.hmrc.bindingtariffclassification.model.search.CaseParamsFilter
import uk.gov.hmrc.bindingtariffclassification.model.sort.CaseSort
import uk.gov.hmrc.bindingtariffclassification.repository.{CaseRepository, SequenceRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class CaseService @Inject()(caseRepository: CaseRepository,
                            crypto: Crypto,
                            sequenceRepository: SequenceRepository,
                            eventService: EventService) {

  def insert(c: Case): Future[Case] = {
    val encryptedCase = crypto.encrypt(c)
    caseRepository.insert(encryptedCase).map(crypto.decrypt)
  }

  def nextCaseReference: Future[String] = {
    sequenceRepository.incrementAndGetByName("case").map(_.value.toString)
  }

  def update(c: Case, upsert: Boolean): Future[Option[Case]] = {
    val encryptedCase = crypto.encrypt(c)
    caseRepository.update(encryptedCase, upsert) map decryptOptionalCase
  }

  def getByReference(reference: String): Future[Option[Case]] = {
    caseRepository.getByReference(reference) map decryptOptionalCase
  }

  private def decryptOptionalCase: PartialFunction[Option[Case], Option[Case]] = {
    case Some(c: Case) => Some(crypto.decrypt(c))
    case _ => None
  }

  def get(searchBy: CaseParamsFilter, sortBy: Option[CaseSort]): Future[Seq[Case]] = {
    caseRepository.get(searchBy, sortBy).map {
      _ map crypto.decrypt
    }
  }

  def deleteAll(): Future[Unit] = {
    caseRepository.deleteAll()
  }

  def incrementDaysElapsed(increment: Double): Future[Int] = {
    caseRepository.incrementDaysElapsed(increment)
  }

}
