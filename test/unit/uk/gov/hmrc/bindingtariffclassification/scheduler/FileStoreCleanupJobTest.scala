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

package uk.gov.hmrc.bindingtariffclassification.scheduler

import java.time._

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, refEq}
import org.mockito.BDDMockito.given
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.config.{AppConfig, JobConfig}
import uk.gov.hmrc.bindingtariffclassification.connector.FileStoreConnector
import uk.gov.hmrc.bindingtariffclassification.model.filestore.{FileMetadata, FileSearch}
import uk.gov.hmrc.bindingtariffclassification.model.{Paged, Pagination}
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future.{failed, successful}
import scala.concurrent.duration._

class FileStoreCleanupJobTest extends BaseSpec with BeforeAndAfterEach {

  private val appConfig          = mock[AppConfig]
  private val caseService        = mock[CaseService]
  private val fileStoreConnector = mock[FileStoreConnector]

  private final val emulatedFailure = new RuntimeException("Emulated failure.")
  private val fileSearch            = FileSearch(published = Some(true))
  private val pageSize              = 100

  override def afterEach(): Unit = {
    super.afterEach()
    reset(appConfig, caseService, fileStoreConnector)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    given(caseService.attachmentExists(any[String])).willReturn(false)
  }

  "Scheduled Job" should {

    val runTime   = LocalTime.of(14, 0)
    val jobConfig = JobConfig(enabled = true, elapseTime = runTime, interval = 1.day)

    "Configure 'Name'" in {
      newJob.name shouldBe "FileStoreCleanup"
    }

    "Configure 'enabled'" in {
      given(appConfig.fileStoreCleanup).willReturn(jobConfig)

      newJob.enabled shouldBe true
    }

    "Configure 'firstRunTime'" in {
      given(appConfig.fileStoreCleanup).willReturn(jobConfig)

      newJob.firstRunTime shouldBe runTime
    }

    "Configure 'interval'" in {
      given(appConfig.fileStoreCleanup).willReturn(jobConfig)

      newJob.interval shouldBe 1.day
    }

  }

  "Scheduled Job 'Execute'" should {
    "Do nothing if no files are found" in {
      givenNoUploadedFiles()

      await(newJob.execute())

      verifyNoFilesDeleted()
    }

    "Do nothing if all files are attached to a case" in {
      givenUploadedFiles(Set("id1", "id2"))
      givenCaseFiles(Set("id1", "id2"))

      await(newJob.execute())

      verifyNoFilesDeleted()
    }

    "Delete files that are not attached to a case" in {
      givenUploadedFiles(Set("id1", "id2", "id3", "id4"))
      givenCaseFiles(Set("id1", "id2"))
      givenFilesDeleteSuccessfully(Set("id3", "id4"))

      await(newJob.execute())

      theFilesDeleted shouldBe List("id3", "id4")
    }

    "Handle errors when deleting files and continue" in {
      givenUploadedFiles(Set("id1", "id2", "id3", "id4"))
      givenCaseFiles(Set("id1", "id2"))
      givenFilesDeleteUnsuccessfully(Set("id3"))
      givenFilesDeleteSuccessfully(Set("id4"))

      await(newJob.execute())

      theFilesDeleted shouldBe List("id3", "id4")
    }

    "Handle multiple pages of files" in {
      givenUploadedFiles(Set("id1", "id2", "id3", "id4", "id5", "id6", "id7"), pageSize = 1)
      givenCaseFiles(Set("id1", "id2", "id3", "id4", "id5"))
      givenFilesDeleteSuccessfully(Set("id6", "id7"))

      await(newJob.execute())

      theFilesDeleted shouldBe List("id6", "id7")
    }
  }

  private def newJob: FileStoreCleanupJob =
    new FileStoreCleanupJob(appConfig, caseService, fileStoreConnector)

  private def givenNoUploadedFiles(): Unit =
    given(fileStoreConnector.find(refEq(fileSearch), refEq(Pagination(page = 1)))(any[HeaderCarrier]))
      .willReturn(successful(Paged(Seq.empty[FileMetadata], Pagination(page = 1), 0)))

  private def givenUploadedFiles(ids: Set[String], pageSize: Int = pageSize): Unit =
    ids.grouped(pageSize).zipWithIndex.foreach {
      case (idSubset, index) =>
        val page = index + 1
        given(fileStoreConnector.find(refEq(fileSearch), refEq(Pagination(page = page)))(any[HeaderCarrier]))
          .willReturn(
            successful(
              Paged(
                idSubset
                  .map(id =>
                    FileMetadata(
                      id          = id,
                      fileName    = "fileName",
                      mimeType    = "mimeType",
                      url         = None,
                      scanStatus  = None,
                      publishable = true,
                      published   = true,
                      lastUpdated = Instant.now()
                    )
                  )
                  .toSeq,
                Pagination(page = index + 1, pageSize = pageSize),
                ids.size
              )
            )
          )
    }

  private def givenCaseFiles(ids: Set[String]): Unit =
    ids.map(id => given(caseService.attachmentExists(refEq(id))).willReturn(true))

  private def givenFilesDeleteSuccessfully(ids: Set[String]): Unit =
    ids.map(id => given(fileStoreConnector.delete(refEq(id))(any[HeaderCarrier])).willReturn(successful(())))

  private def givenFilesDeleteUnsuccessfully(ids: Set[String]): Unit =
    ids.map(id => given(fileStoreConnector.delete(refEq(id))(any[HeaderCarrier])).willReturn(failed(emulatedFailure)))

  private def verifyNoFilesDeleted(): Unit =
    verify(fileStoreConnector, never()).delete(any[String])(any[HeaderCarrier])

  private def theFilesDeleted: List[String] = {
    import scala.collection.JavaConverters._

    val captor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    verify(fileStoreConnector, atLeastOnce()).delete(captor.capture())(any[HeaderCarrier])
    captor.getAllValues.asScala.toList
  }
}