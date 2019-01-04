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

package uk.gov.hmrc.bindingtariffclassification.config

import java.time.{Clock, LocalTime}

import javax.inject._
import play.api.Mode.Mode
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.duration.FiniteDuration

@Singleton
class AppConfig @Inject()(val runModeConfiguration: Configuration, environment: Environment) extends ServicesConfig {

  override protected def mode: Mode = environment.mode

  lazy val isDeleteEnabled: Boolean = getBooleanConfig("deleteEnabled", default = false)

  lazy val clock: Clock = Clock.systemDefaultZone()

  lazy val daysElapsed: JobConfig = JobConfig(
    LocalTime.parse(getString("scheduler.days-elapsed.run-time")),
    getDuration("scheduler.days-elapsed.interval").asInstanceOf[FiniteDuration]
  )

  private def getBooleanConfig(key: String, default: Boolean): Boolean = {
    runModeConfiguration.getBoolean(key).getOrElse(default)
  }

  def bankHolidaysUrl: String = {
    val protocol = getString("microservice.services.bank-holidays.protocol")
    val host = getString("microservice.services.bank-holidays.host")
    val port = runModeConfiguration.getInt("microservice.services.bank-holidays.port")
    s"$protocol://$host${port.map(p => s":$p").getOrElse("")}"
  }

}

case class JobConfig(elapseTime: LocalTime, interval: FiniteDuration)
