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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.metrics.HasMetrics
import uk.gov.hmrc.bindingtariffclassification.model.filestore.{FileMetadata, FileSearch}
import uk.gov.hmrc.bindingtariffclassification.model.{Paged, Pagination}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FileStoreConnector @Inject() (appConfig: AppConfig, http: HttpClientV2, hasMetrics: HasMetrics)(implicit
  mat: Materializer
) {

  implicit val ec: ExecutionContext = mat.executionContext

  private lazy val ParamLength = 42 // A 36-char UUID plus &id= and some wiggle room
  private lazy val BatchSize =
    ((appConfig.maxUriLength - appConfig.fileStoreUrl.length) / ParamLength).intValue()

  private def addHeaders(implicit hc: HeaderCarrier): Seq[(String, String)] = {

    val headerName: String = "X-Api-Token"

    hc.headers(Seq(headerName)) match {
      case header @ Seq(_) => header
      case _               => Seq(headerName -> appConfig.authorization)
    }
  }

  private def findQueryUri(search: FileSearch, pagination: Pagination): String = {
    val queryParams = FileSearch.bindable.unbind("", search) + "&" + Pagination.bindable.unbind("", pagination)
    s"${appConfig.fileStoreUrl}/file?$queryParams"
  }

  def find(search: FileSearch, pagination: Pagination)(implicit hc: HeaderCarrier): Future[Paged[FileMetadata]] =
    hasMetrics.withMetricsTimerAsync("find-attachments") { _ =>
      if (search.ids.exists(_.nonEmpty) && pagination.equals(Pagination.max)) {
        Source(search.ids.get)
          .grouped(BatchSize)
          .mapAsyncUnordered(Runtime.getRuntime.availableProcessors()) { idBatch =>
            http
              .get(url"${findQueryUri(search.copy(ids = Some(idBatch.toSet)), Pagination.max)}")
              .setHeader(
                addHeaders: _*
              )
              .execute[Paged[FileMetadata]]
          }
          .runFold(Seq.empty[FileMetadata]) { case (acc, next) =>
            acc ++ next.results
          }
          .map(results => Paged(results = results, pagination = Pagination.max, resultCount = results.size.toLong))
      } else {
        http
          .get(url"${findQueryUri(search, pagination)}")
          .setHeader(addHeaders: _*)
          .execute[Paged[FileMetadata]]
      }
    }

  def delete(id: String)(implicit hc: HeaderCarrier): Future[Unit] =
    hasMetrics
      .withMetricsTimerAsync("delete-attachment") { _ =>
        http
          .delete(url"${appConfig.fileStoreUrl}/file/$id")
          .setHeader(addHeaders: _*)
          .execute[HttpResponse]
      }
      .map(_ => ())
}
