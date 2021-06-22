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

package uk.gov.hmrc.bindingtariffclassification.connector

import com.kenshoo.play.metrics.Metrics
import play.api.libs.json.Json
import uk.gov.hmrc.bindingtariffclassification.common.Logging
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.http.ProxyHttpClient
import uk.gov.hmrc.bindingtariffclassification.metrics.HasMetrics
import uk.gov.hmrc.bindingtariffclassification.model.BankHolidaysResponse
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters.formatBankHolidaysResponse
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

@Singleton
class BankHolidaysConnector @Inject() (appConfig: AppConfig, http: ProxyHttpClient, val metrics: Metrics)(
  implicit executionContext: ExecutionContext
) extends Logging
    with HasMetrics {
  def get()(implicit headerCarrier: HeaderCarrier): Future[Set[LocalDate]] =
    withMetricsTimerAsync("get-bank-holidays") { _ =>
      http
        .GET[BankHolidaysResponse](s"${appConfig.bankHolidaysUrl}/bank-holidays")
        .recover(withResourcesFile)
        .map(_.`england-and-wales`.events.map(_.date).toSet)
    }

  private def withResourcesFile: PartialFunction[Throwable, BankHolidaysResponse] = {
    case t =>
      logger.error("Bank Holidays Request Failed", t)
      val url     = getClass.getClassLoader.getResource("bank-holidays-fallback.json")
      val content = Source.fromURL(url, "UTF-8").getLines().mkString
      Json.fromJson(Json.parse(content)).get
  }

}
