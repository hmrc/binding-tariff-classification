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

package uk.gov.hmrc.bindingtariffclassification.service

import akka.stream.Materializer
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.repository.{CaseAttachmentAggregation, CaseRepository, MigrationLockRepository, SequenceRepository}

import java.time.Instant
import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CaseService @Inject() (
  appConfig: AppConfig,
  caseRepository: CaseRepository,
  sequenceRepository: SequenceRepository,
  migrationRepository: MigrationLockRepository,
  caseAttachmentAggregation: CaseAttachmentAggregation,
  eventService: EventService
)(implicit mat: Materializer) {

  implicit val ec: ExecutionContext = mat.executionContext

  def insert(c: Case): Future[Case] =
    caseRepository.insert(c)

  def addInitialSampleStatusIfExists(c: Case): Future[Unit] = {
    if (c.sample.status.nonEmpty) {
      val details = SampleStatusChange(None, c.sample.status, None)
      eventService.insert(
        Event(
          UUID.randomUUID().toString,
          details,
          Operator("-1", Some(c.application.contact.name)),
          c.reference,
          Instant.now()
        )
      )
    }
    Future.successful((): Unit)
  }

  def nextCaseReference(applicationType: ApplicationType.Value): Future[String] = {
    val nextCaseReference = applicationType match {
      case ApplicationType.BTI =>
        getNextInSequence("ATaR Case Reference", appConfig.atarCaseReferenceOffset)
      case _ =>
        getNextInSequence("Other Case Reference", appConfig.otherCaseReferenceOffset)
    }

    nextCaseReference.map(_.toString)
  }

  private def getNextInSequence(sequenceName: String, offset: Long): Future[Long] =
    sequenceRepository.incrementAndGetByName(sequenceName).map {
      _.value + offset
    }

  def update(c: Case, upsert: Boolean): Future[Option[Case]] =
    caseRepository.update(c, upsert)

  def update(reference: String, caseUpdate: CaseUpdate): Future[Option[Case]] =
    caseRepository.update(reference, caseUpdate)

  def getByReference(reference: String): Future[Option[Case]] =
    caseRepository.getByReference(reference)

  def get(search: CaseSearch, pagination: Pagination): Future[Paged[Case]] =
    caseRepository.get(search, pagination)

  def deleteAll(): Future[Unit] = {
    caseRepository.deleteAll()
    sequenceRepository.deleteSequenceByName("ATaR Case Reference")
    sequenceRepository.deleteSequenceByName("Other Case Reference")
    migrationRepository.deleteAll()
  }

  def delete(reference: String): Future[Unit] =
    caseRepository.delete(reference)

  def attachmentExists(attachmentId: String): Future[Boolean] =
    caseAttachmentAggregation.find(attachmentId).map(_.isDefined)

  def refreshAttachments(): Future[Unit] =
    caseAttachmentAggregation.refresh()
}
