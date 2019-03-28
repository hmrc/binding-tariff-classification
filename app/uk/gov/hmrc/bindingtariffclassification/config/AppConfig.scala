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
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.duration.FiniteDuration

@Singleton
class AppConfig @Inject()(val runModeConfiguration: Configuration, environment: Environment) extends ServicesConfig {

  override protected def mode: Mode = environment.mode

  lazy val isTestMode: Boolean = getBooleanConfig("testMode", default = false)

  lazy val caseReferenceStart: Long = runModeConfiguration.getLong("case-reference-start").getOrElse(504400000)

  lazy val clock: Clock = Clock.systemUTC()

  lazy val daysElapsed: JobConfig = JobConfig(
    LocalTime.parse(getString("scheduler.days-elapsed.run-time")),
    getDuration("scheduler.days-elapsed.interval").asInstanceOf[FiniteDuration]
  )

  lazy val authorization: String = getString("auth.api-token")

  private def getBooleanConfig(key: String, default: Boolean): Boolean = {
    runModeConfiguration.getBoolean(key).getOrElse(default)
  }

  def bankHolidaysUrl: String = baseUrl("bank-holidays")

  lazy val upsertAgents: Seq[String] = getString("upsert-permitted-agents").split(",").filter(_.nonEmpty)

  lazy val mongoEncryption: MongoEncryption = {
    val encryptionEnabled = getBooleanConfig("mongodb.encryption.enabled", default = false)
    val encryptionKey = {
      if (encryptionEnabled) Some(getString("mongodb.encryption.key"))
      else None
    }

    if (encryptionEnabled && encryptionKey.isDefined) Logger.info("Mongo encryption enabled")
    else Logger.info("Mongo encryption disabled")

    MongoEncryption(encryptionEnabled, encryptionKey)
  }

}

case class MongoEncryption(enabled: Boolean = false, key: Option[String] = None)
case class JobConfig(elapseTime: LocalTime, interval: FiniteDuration)
