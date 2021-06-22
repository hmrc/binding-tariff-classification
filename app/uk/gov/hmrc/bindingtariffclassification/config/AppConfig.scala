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

package uk.gov.hmrc.bindingtariffclassification.config

import org.quartz.CronExpression
import play.api.Configuration
import uk.gov.hmrc.bindingtariffclassification.common.Logging
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Clock
import javax.inject._

@Singleton
class AppConfig @Inject() (
  val configuration: Configuration,
  config: ServicesConfig
) extends Logging {

  private def configNotFoundError(key: String): Nothing =
    throw new RuntimeException(s"Could not find config key '$key'")

  lazy val isTestMode: Boolean = getBooleanConfig("testMode")

  lazy val atarCaseReferenceOffset: Long  = configuration.get[Long]("atar-case-reference-offset")
  lazy val otherCaseReferenceOffset: Long = configuration.get[Long]("other-case-reference-offset")

  lazy val clock: Clock = Clock.systemUTC()

  lazy val activeDaysElapsed: JobConfig = JobConfig(
    "ActiveDaysElapsed",
    getBooleanConfig("scheduler.active-days-elapsed.enabled"),
    new CronExpression(getString("scheduler.active-days-elapsed.schedule"))
  )

  lazy val referredDaysElapsed: JobConfig = JobConfig(
    "ReferredDaysElapsed",
    getBooleanConfig("scheduler.referred-days-elapsed.enabled"),
    new CronExpression(getString("scheduler.referred-days-elapsed.schedule"))
  )

  lazy val fileStoreCleanup: JobConfig = JobConfig(
    "FileStoreCleanup",
    getBooleanConfig("scheduler.filestore-cleanup.enabled"),
    new CronExpression(getString("scheduler.filestore-cleanup.schedule"))
  )

  lazy val authorization: String = configuration.get[String]("auth.api-token")

  private def getBooleanConfig(key: String, default: Boolean = false): Boolean =
    configuration.getOptional[Boolean](key).getOrElse(default)

  lazy val fileStoreUrl: String    = config.baseUrl("binding-tariff-filestore")
  lazy val bankHolidaysUrl: String = config.baseUrl("bank-holidays")

  lazy val upsertAgents: Seq[String] =
    configuration.get[String]("upsert-permitted-agents").split(",").filter(_.nonEmpty)

  def getString(key: String): String =
    configuration.getOptional[String](key).getOrElse(configNotFoundError(key))

  lazy val mongodbUri: String = configuration.get[String]("mongodb.uri")
  lazy val appName: String = configuration.get[String]("appName")

  lazy val mongoEncryption: MongoEncryption = {
    val encryptionEnabled = getBooleanConfig("mongodb.encryption.enabled")
    val encryptionKey = {
      if (encryptionEnabled) Some(getString("mongodb.encryption.key"))
      else None
    }

    if (encryptionEnabled && encryptionKey.isDefined) logger.info("Mongo encryption enabled")
    else logger.info("Mongo encryption disabled")

    MongoEncryption(encryptionEnabled, encryptionKey)
  }

  lazy val maxUriLength: Long = configuration.underlying.getBytes("akka.http.parsing.max-uri-length")

}

case class MongoEncryption(enabled: Boolean = false, key: Option[String] = None)

case class JobConfig(name: String, enabled: Boolean, schedule: CronExpression)
