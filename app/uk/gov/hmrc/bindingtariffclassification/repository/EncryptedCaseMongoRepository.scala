/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.repository

import uk.gov.hmrc.bindingtariffclassification.crypto.Crypto
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.reporting._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EncryptedCaseMongoRepository @Inject() (repository: CaseMongoRepository, crypto: Crypto)(implicit
  ec: ExecutionContext
) extends CaseRepository {

  private def encrypt: Case => Case = crypto.encrypt

  private def decrypt: Case => Case = crypto.decrypt

  override def insert(c: Case): Future[Case] = repository.insert(encrypt(c)).map(decrypt)

  override def update(c: Case, upsert: Boolean): Future[Option[Case]] =
    repository.update(encrypt(c), upsert).map(_.map(decrypt))

  override def update(reference: String, caseUpdate: CaseUpdate): Future[Option[Case]] =
    repository.update(reference, caseUpdate).map(_.map(decrypt))

  override def getByReference(reference: String): Future[Option[Case]] =
    repository.getByReference(reference).map(_.map(decrypt))

  override def get(search: CaseSearch, pagination: Pagination): Future[Paged[Case]] =
    repository.get(enryptSearch(search), pagination).map(_.map(decrypt))

  override def getAllByEori(eori: String): Future[List[Case]] =
    repository.getAllByEori(crypto.encryptString.apply(eori)).map(_.map(decrypt))

  override def deleteAll(): Future[Unit] = repository.deleteAll()

  override def delete(reference: String): Future[Unit] = repository.delete(reference)

  private def enryptSearch(search: CaseSearch) = {
    val eoriEnc: Option[String] = search.filter.eori.map(crypto.encryptString)
    search.copy(filter = search.filter.copy(eori = eoriEnc))
  }

  override def summaryReport(report: SummaryReport, pagination: Pagination): Future[Paged[ResultGroup]] =
    repository.summaryReport(report, pagination)

  override def caseReport(
    report: CaseReport,
    pagination: Pagination
  ): Future[Paged[Map[String, ReportResultField[?]]]] = {
    val fReport = repository.caseReport(report, pagination)

    val encryptedFieldNames = ReportField.encryptedFields.map(_.fieldName)

    fReport.map { pagedResult =>
      val result: Seq[Map[String, ReportResultField[?]]] = pagedResult.results.map(casesResult =>
        casesResult.map { case (fieldName, fieldValue) =>
          fieldValue match {
            case _ @StringResultField(_, data) if encryptedFieldNames.contains(fieldName) =>
              val decryptedField = data.map(crypto.decryptString)
              (fieldName, StringResultField(fieldName, decryptedField))
            case _ =>
              (fieldName, fieldValue)
          }
        }
      )
      pagedResult.copy(results = result)
    }
  }

  override def queueReport(
    report: QueueReport,
    pagination: Pagination
  ): Future[Paged[QueueResultGroup]] =
    repository.queueReport(report, pagination)

  override def getGroupedCasesByKeyword(pagination: Pagination): Future[Paged[CaseKeyword]] =
    repository.getGroupedCasesByKeyword(pagination)
}
