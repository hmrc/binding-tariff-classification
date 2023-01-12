/*
 * Copyright 2023 HM Revenue & Customs
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

import akka.actor.ActorSystem
import com.github.tomakehurst.wiremock.client.WireMock._
import org.mockito.BDDMockito.given
import org.scalatest.BeforeAndAfterAll
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.filestore.{FileMetadata, FileSearch, ScanStatus}
import uk.gov.hmrc.bindingtariffclassification.model.{Paged, Pagination}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import util.TestMetrics

import java.time.Instant
import java.util.UUID

class FileStoreConnectorTest extends BaseSpec with WiremockTestServer with BeforeAndAfterAll {

  private val config = mock[AppConfig]

  private implicit val headers: HeaderCarrier   = HeaderCarrier()
  private implicit val actorSystem: ActorSystem = ActorSystem("test")

  private val appConfig = fakeApplication.injector.instanceOf[AppConfig]

  private val defaultHttpClient: DefaultHttpClient = fakeApplication.injector.instanceOf[DefaultHttpClient]

  private val connector = new FileStoreConnector(config, defaultHttpClient, new TestMetrics)

  private val maxUriLength = 2048

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    given(config.fileStoreUrl).willReturn(wireMockUrl)
    given(config.maxUriLength).willReturn(maxUriLength)
    given(config.authorization).willReturn(appConfig.authorization)
  }

  private val uploadedFile: FileMetadata = FileMetadata(
    id          = "id",
    fileName    = "file-name.txt",
    mimeType    = "text/plain",
    url         = None,
    scanStatus  = Some(ScanStatus.READY),
    publishable = true,
    published   = true,
    lastUpdated = Instant.now
  )

  private val fileStoreResponse: String = Json.toJson(Paged(Seq(uploadedFile), Pagination(), 1)).toString

  "find" should {

    "GET from the File Store" in {
      stubFor(
        get("/file?id=id&page=1&page_size=2")
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
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

    "use multiple requests to the File Store" in {
      val batchSize  = 48
      val numBatches = 5
      val ids        = (1 to batchSize * numBatches).map(_ => UUID.randomUUID().toString).toSet

      stubFor(
        get(urlMatching(s"/file\\?(&?id=[a-f0-9-]+)+&page=1&page_size=2147483647"))
          .willReturn(
            aResponse()
              .withStatus(Status.OK)
              .withBody(fileStoreResponse)
          )
      )

      await(connector.find(FileSearch(ids = Some(ids)), Pagination.max)).results shouldBe (1 to numBatches).map(_ =>
        uploadedFile
      )
    }

  }

  "delete" should {
    "DELETE from the File Store" in {
      stubFor(
        delete("/file/id")
          .willReturn(
            aResponse()
              .withStatus(Status.NO_CONTENT)
          )
      )

      await(connector.delete("id"))

      verify(
        deleteRequestedFor(urlEqualTo("/file/id"))
          .withHeader("X-Api-Token", equalTo(appConfig.authorization))
      )
    }
  }
}
