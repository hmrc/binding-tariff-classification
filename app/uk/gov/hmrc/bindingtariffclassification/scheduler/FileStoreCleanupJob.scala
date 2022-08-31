/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.scheduler

import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import uk.gov.hmrc.bindingtariffclassification.common.Logging
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.connector.FileStoreConnector
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.filestore.{FileMetadata, FileSearch}
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.LockRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future._
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future, duration}
import scala.util.control.NonFatal

@Singleton
class FileStoreCleanupJob @Inject() (
  caseService: CaseService,
  fileStoreConnector: FileStoreConnector,
  mongoLockRepository: LockRepository,
  implicit val appConfig: AppConfig
)(implicit ec: ExecutionContext, mat: Materializer)
    extends ScheduledJob
    with Logging {

  override val jobConfig = appConfig.fileStoreCleanup

  override val lockRepository: LockRepository = mongoLockRepository
  override val lockId: String                 = "filestore_cleanup"
  override val ttl: duration.Duration         = 5.minutes

  private implicit val carrier: HeaderCarrier = HeaderCarrier()
  private lazy val criteria = FileSearch(
    published = Some(true)
  )

  override def execute(): Future[Unit] =
    caseService.refreshAttachments().flatMap { _ =>
      fileStoreConnector
        .find(criteria, Pagination())
        .flatMap(info => if (info.pageCount >= 1) processPage(info.pageCount) else successful(()))
    }

  private def processPage(page: Int): Future[Unit] =
    fileStoreConnector.find(criteria, Pagination(page = page)) flatMap { pagedFiles =>
      processFiles(pagedFiles.results).map(_ => pagedFiles)
    } flatMap {
      case pagedFiles if pagedFiles.pageIndex > 1 => processPage(page - 1)
      case _                                      => successful(())
    }

  private def processFiles(files: Seq[FileMetadata]): Future[Unit] =
    Source(files.toList)
      .mapAsync(1) { file =>
        caseService.attachmentExists(file.id).map {
          case true  => successful(())
          case false => deleteFile(file)
        }
      }
      .runWith(Sink.ignore)
      .map(_ => ())

  private def deleteFile(file: FileMetadata): Future[Unit] = {
    logger.info(s"$name: Removing file [${file.fileName}] with id [${file.id}]")
    fileStoreConnector.delete(file.id) recoverWith {
      case NonFatal(e) =>
        Future {
          logger.error(s"$name: Failed to remove file [${file.fileName}] with id [${file.id}]", e)
        }
    }
  }
}
