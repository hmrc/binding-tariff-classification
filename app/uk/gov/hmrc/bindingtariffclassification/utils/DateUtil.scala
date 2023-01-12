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

package uk.gov.hmrc.bindingtariffclassification.utils

import java.time.{DayOfWeek, Instant, LocalDate}

import uk.gov.hmrc.bindingtariffclassification.config.AppConfig

object DateUtil {

  def toInstant(localDate: LocalDate)(implicit appConfig: AppConfig): Instant =
    localDate.atStartOfDay(appConfig.clock.getZone).toInstant

  def bankHoliday(date: LocalDate)(implicit bankHolidays: Set[LocalDate]): Boolean = bankHolidays.contains(date)

  def weekend(date: LocalDate): Boolean = Set(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).contains(date.getDayOfWeek)

}
