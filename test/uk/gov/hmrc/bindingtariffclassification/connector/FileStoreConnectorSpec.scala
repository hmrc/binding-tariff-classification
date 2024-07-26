/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.connector

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.filestore.{FileMetadata, FileSearch, ScanStatus}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps}

import java.time.Instant
import scala.concurrent.Future

class FileStoreConnectorSpec extends BaseSpec {

  private val uploadedFile: FileMetadata =
    FileMetadata(
      id = "id",
      fileName = "file-name.txt",
      mimeType = "text/plain",
      url = None,
      scanStatus = Some(ScanStatus.READY),
      publishable = true,
      published = true,
      lastUpdated = Instant.now
    )

  object TestConnector extends FileStoreConnector(fakeAppConfig, mockHttpClient, FakeHasMetrics)

  "FileStoreConnector" when {

    ".find()" when {

      "the fileStoreResponse is less the Paged max value and a successful response" should {

        "return the file metadata paged" in {

          val fileSearch: FileSearch = FileSearch(ids = Some(Set("id")))
          val pagination             = Pagination(1, 2)

          val fileStoreResponse: Paged[FileMetadata] =
            Paged(Seq(uploadedFile), pagination, 1)

          val queryParams: String =
            FileSearch.bindable.unbind("", fileSearch) + "&" + Pagination.bindable.unbind("", pagination)

          val fileStoreBaseUrl: String = "http://localhost:9583"

          val fullURL: String = fileStoreBaseUrl + s"/file?$queryParams"

          mockFileStoreUrl(fullURL)

          when(mockAppConfig.authorization).thenReturn("fake-api-token")
          when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
          when(mockRequestBuilder.execute(any[HttpReads[Paged[FileMetadata]]], any()))
            .thenReturn(Future(fileStoreResponse))

          when(mockHttpClient.get(ArgumentMatchers.eq(url"$fullURL"))(any[HeaderCarrier]()))
            .thenReturn(mockRequestBuilder)

          val actual: Future[Paged[FileMetadata]] = TestConnector.find(fileSearch, pagination)
          val expected: Paged[FileMetadata]       = fileStoreResponse

          await(actual) shouldBe expected
        }
      }

      "the fileStoreResponse is batched, Paged max value and a successful response" should {

        "return the file metadata max page size" in {

          val fileSearch: FileSearch = FileSearch(ids = Some(Set("id")))
          val pagination             = Pagination.max

          val fileStoreResponse: Paged[FileMetadata] =
            Paged(Seq(uploadedFile), pagination, 1)

          val queryParams: String =
            FileSearch.bindable.unbind("", fileSearch) + "&" + Pagination.bindable.unbind("", pagination)

          val fileStoreBaseUrl: String = "http://localhost:9583"

          val fullURL: String = fileStoreBaseUrl + s"/file?$queryParams"

          mockFileStoreUrl(fullURL)

          when(mockAppConfig.authorization).thenReturn("fake-api-token")
          when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
          when(mockRequestBuilder.execute(any[HttpReads[Paged[FileMetadata]]], any()))
            .thenReturn(Future(fileStoreResponse))

          when(mockHttpClient.get(ArgumentMatchers.eq(url"$fullURL"))(any[HeaderCarrier]()))
            .thenReturn(mockRequestBuilder)

          val actual: Future[Paged[FileMetadata]] = TestConnector.find(fileSearch, Pagination.max)
          val expected: Paged[FileMetadata]       = fileStoreResponse

          await(actual) shouldBe expected
        }
      }
    }

    ".delete()" when {

      "a successful delete response" should {

        "return () as a result" in {

          val fileStoreBaseUrl: String = "http://localhost:9583"
          val fullURL: String          = fileStoreBaseUrl + s"/file/id"

          mockFileStoreUrl(fullURL)

          when(mockAppConfig.authorization).thenReturn("fake-api-token")
          when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
          when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any()))
            .thenReturn(Future(HttpResponse(204)))
          when(mockHttpClient.delete(ArgumentMatchers.eq(url"$fullURL"))(any[HeaderCarrier]()))
            .thenReturn(mockRequestBuilder)

          val actual: Future[Unit] = TestConnector.delete("id")
          val expected: Unit       = ()

          await(actual) shouldBe expected
        }
      }
    }
  }
}
