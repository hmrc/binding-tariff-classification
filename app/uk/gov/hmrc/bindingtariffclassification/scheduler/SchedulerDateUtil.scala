/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time.{Instant, LocalDate, LocalTime}

import javax.inject.{Inject, Singleton}
import javax.inject.Inject

import uk.gov.hmrc.bindingtariffclassification.config.AppConfig

import scala.concurrent.duration.FiniteDuration

@Singleton
class SchedulerDateUtil @Inject()(appConfig: AppConfig) {

  private lazy val clock = appConfig.clock

  def nextRun(offset: LocalTime, interval: FiniteDuration): Instant = {
    val time = LocalTime.now(clock)

    val offsetSeconds: Int = offset.toSecondOfDay
    val currentSeconds: Int = time.toSecondOfDay

    val intervalSeconds: Long = interval.toSeconds
    val deltaSeconds: Int = offsetSeconds - currentSeconds
    val intervalRemainder: Long = deltaSeconds % intervalSeconds

    val intervalRemaining: Long = if(intervalRemainder < 0) intervalRemainder + intervalSeconds else intervalRemainder
    LocalDate
      .now(clock)
      .atTime(time)
      .plusSeconds(intervalRemaining)
      .atZone(clock.getZone)
      .toInstant
  }

  def closestRun(offset: LocalTime, interval: FiniteDuration): Instant = {
    val time = LocalTime.now(clock)

    val offsetSeconds: Int = offset.toSecondOfDay
    val currentSeconds: Int = time.toSecondOfDay

    val intervalSeconds: Long = interval.toSeconds
    val deltaSeconds: Int = offsetSeconds - currentSeconds
    val intervalRemainder: Long = deltaSeconds % intervalSeconds

    val intervalRemaining: Long = if(intervalRemainder < 0) intervalRemainder + intervalSeconds else intervalRemainder
    val intervalElapsed: Long = intervalSeconds - intervalRemaining
    if (intervalRemaining == 0) {
      LocalDate.now(clock).atTime(time).atZone(clock.getZone).toInstant
    } else if (intervalRemaining < intervalElapsed) {
      LocalDate.now(clock).atTime(time).plusSeconds(intervalRemaining).atZone(clock.getZone).toInstant
    } else {
      LocalDate.now(clock).atTime(time).minusSeconds(intervalElapsed).atZone(clock.getZone).toInstant
    }
  }

}
