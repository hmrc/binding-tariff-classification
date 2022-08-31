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

package uk.gov.hmrc.bindingtariffclassification.connector

import javax.inject.{Inject, Singleton}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.filestore.{FileMetadata, FileSearch}
import uk.gov.hmrc.bindingtariffclassification.model.{Paged, Pagination}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import com.kenshoo.play.metrics.Metrics
import uk.gov.hmrc.bindingtariffclassification.metrics.HasMetrics
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

@Singleton
class FileStoreConnector @Inject() (appConfig: AppConfig, http: DefaultHttpClient, val metrics: Metrics)(
  implicit mat: Materializer
) extends HasMetrics {

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

  def find(search: FileSearch, pagination: Pagination)(implicit hc: HeaderCarrier): Future[Paged[FileMetadata]] =
    withMetricsTimerAsync("find-attachments") { _ =>
      if (search.ids.exists(_.nonEmpty) && pagination.equals(Pagination.max)) {
        Source(search.ids.get)
          .grouped(BatchSize)
          .mapAsyncUnordered(Runtime.getRuntime.availableProcessors()) { idBatch =>
            http.GET[Paged[FileMetadata]](
              findQueryUri(search.copy(ids = Some(idBatch.toSet)), Pagination.max),
              headers = addHeaders
            )
          }
          .runFold(Seq.empty[FileMetadata]) {
            case (acc, next) => acc ++ next.results
          }
          .map(results => Paged(results = results, pagination = Pagination.max, resultCount = results.size.toLong))
      } else {
        http.GET[Paged[FileMetadata]](findQueryUri(search, pagination), headers = addHeaders)
      }
    }

  def delete(id: String)(implicit hc: HeaderCarrier): Future[Unit] = withMetricsTimerAsync("delete-attachment") { _ =>
    http.DELETE[HttpResponse](s"${appConfig.fileStoreUrl}/file/$id", headers = addHeaders).map(_ => ())
  }

  private def findQueryUri(search: FileSearch, pagination: Pagination): String = {
    val queryParams = FileSearch.bindable.unbind("", search) + "&" + Pagination.bindable.unbind("", pagination)
    s"${appConfig.fileStoreUrl}/file?$queryParams"
  }
}
