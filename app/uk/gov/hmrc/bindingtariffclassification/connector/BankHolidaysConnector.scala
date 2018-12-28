/*
 * Copyright 2018 HM Revenue & Customs
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

import java.time.LocalDate

import javax.inject.{Inject, Singleton}
import play.api.libs.json.OFormat
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.{BankHolidaysResponse, JsonFormatters}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BankHolidaysConnector @Inject()(appConfig: AppConfig, http: HttpClient)(
  implicit executionContext: ExecutionContext) {

  def get()(implicit headerCarrier: HeaderCarrier): Future[Seq[LocalDate]] = {
    implicit val format: OFormat[BankHolidaysResponse] = JsonFormatters.formatBankHolidaysResponse
    http.GET[BankHolidaysResponse](s"${appConfig.bankHolidaysUrl}/bank-holidays.json")
      .map(_.`england-and-wales`.events.map(_.date))
  }

}
