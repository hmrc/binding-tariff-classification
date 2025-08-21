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

package uk.gov.hmrc.bindingtariffclassification.connector

import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.BDDMockito.given
import play.api.http.Status.{BAD_GATEWAY, NO_CONTENT, OK}
import play.api.libs.json.Json
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.filestore.{FileMetadata, FileSearch, ScanStatus}
import uk.gov.hmrc.bindingtariffclassification.model.{Paged, Pagination}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.http.test.HttpClientV2Support
import util.TestMetrics

import java.time.Instant
import java.util.UUID

class FileStoreConnectorTest extends BaseSpec with WiremockTestServer with HttpClientV2Support {

  private val config = mock[AppConfig]

  private implicit val headers: HeaderCarrier = HeaderCarrier()

  private val appConfig = fakeApplication.injector.instanceOf[AppConfig]

  private val connector = new FileStoreConnector(config, httpClientV2, new TestMetrics)

  private val maxUriLength = 2048

  private trait Test {
    given(config.fileStoreUrl).willReturn(wireMockUrl)
    given(config.maxUriLength).willReturn(maxUriLength)
    given(config.authorization).willReturn(appConfig.authorization)
  }

  private val uploadedFile: FileMetadata = FileMetadata(
    id = "id",
    fileName = "file-name.txt",
    mimeType = "text/plain",
    url = None,
    scanStatus = Some(ScanStatus.READY),
    publishable = true,
    published = true,
    lastUpdated = Instant.now
  )

  private val fileStoreResponse: String = Json.toJson(Paged(Seq(uploadedFile), Pagination(), 1)).toString

  "find" should {

    "GET from the File Store" in new Test {
      stubFor(
        get("/file?id=id&page=1&page_size=2")
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(fileStoreResponse)
          )
      )

      await(connector.find(FileSearch(ids = Some(Set("id"))), Pagination(1, 2))).results shouldBe Seq(
        uploadedFile
      )

      verify(
        getRequestedFor(urlEqualTo("/file?id=id&page=1&page_size=2"))
          .withHeader("X-Api-Token", equalTo(appConfig.authorization))
      )
    }

    "use multiple requests to the File Store" in new Test {
      val batchSize  = 48
      val numBatches = 5
      val ids        = (1 to batchSize * numBatches).map(_ => UUID.randomUUID().toString).toSet

      stubFor(
        get(urlMatching(s"/file\\?(&?id=[a-f0-9-]+)+&page=1&page_size=2147483647"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(fileStoreResponse)
          )
      )

      await(connector.find(FileSearch(ids = Some(ids)), Pagination.max)).results shouldBe (1 to numBatches).map(_ =>
        uploadedFile
      )
    }

  }

  "delete" should {
    "DELETE from the File Store" in new Test {
      stubFor(
        delete("/file/id")
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
          )
      )

      await(connector.delete("id"))

      verify(
        deleteRequestedFor(urlEqualTo("/file/id"))
          .withHeader("X-Api-Token", equalTo(appConfig.authorization))
      )
    }

    "propagate errors" in new Test {
      stubFor(
        delete("/file/id")
          .willReturn(
            aResponse()
              .withStatus(BAD_GATEWAY)
          )
      )

      intercept[UpstreamErrorResponse] {
        await(connector.delete("id"))
      }

      verify(
        deleteRequestedFor(urlEqualTo("/file/id"))
          .withHeader("X-Api-Token", equalTo(appConfig.authorization))
      )
    }
  }
}
