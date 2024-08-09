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

import play.api.libs.json.Json
import uk.gov.hmrc.bindingtariffclassification.common.Logging
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.metrics.HasMetrics
import uk.gov.hmrc.bindingtariffclassification.model.BankHolidaysResponse
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters.formatBankHolidaysResponse
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

@Singleton
class BankHolidaysConnector @Inject() (appConfig: AppConfig, http: HttpClientV2, val metrics: Metrics)(implicit
  executionContext: ExecutionContext
) extends Logging
    with HasMetrics {

  //updated to match code on main
  def get()(implicit headerCarrier: HeaderCarrier): Future[Set[LocalDate]] =
    withMetricsTimerAsync("get-bank-holidays") { _ =>
      http
        .get(url"${appConfig.bankHolidaysUrl}")
        .withProxy
        .execute[BankHolidaysResponse]
    }
      .recover(withResourcesFile)
      .map(_.`england-and-wales`.events.map(_.date).toSet)

  private def withResourcesFile: PartialFunction[Throwable, BankHolidaysResponse] = { case t =>
    logger.error("Bank Holidays Request Failed", t)
    val url    = getClass.getClassLoader.getResource("bank-holidays-fallback.json")
    val source = Source.fromURL(url, StandardCharsets.UTF_8.name())
    val content =
      try source.getLines().mkString
      finally source.close()
    Json.fromJson(Json.parse(content)).get
  }

}
