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

import java.time.{LocalTime, ZoneOffset}

import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration._

class AppConfigTest extends UnitSpec {

  private def configWith(pairs: (String, String)*): AppConfig = {
    new AppConfig(Configuration.from(pairs.map(e => e._1 -> e._2).toMap), Environment.simple())
  }

  "Config" should {

    "build 'clock'" in {
      configWith().clock.getZone shouldBe ZoneOffset.UTC
    }

    "build 'isTestMode" in {
      configWith("testMode" -> "true").isTestMode shouldBe true
      configWith("testMode" -> "false").isTestMode shouldBe false
    }

    "build 'DaysElapsedConfig" in {
      val config = configWith(
        "scheduler.days-elapsed.run-time" -> "00:00",
        "scheduler.days-elapsed.interval" -> "1d"
      ).daysElapsed
      config.elapseTime shouldBe LocalTime.of(0, 0, 0)
      config.interval shouldBe 1.days
    }

    "build 'bankHolidaysUrl" in {
      configWith(
        "microservice.services.bank-holidays.protocol" -> "https",
        "microservice.services.bank-holidays.host" -> "www.host.co.uk",
        "microservice.services.bank-holidays.port" -> "123"
      ).bankHolidaysUrl shouldBe "https://www.host.co.uk:123"
    }

    "build 'upsert-permitted-agents'" in {
      configWith("upsert-permitted-agents" -> "x,y").upsertAgents shouldBe Seq("x", "y")
      configWith("upsert-permitted-agents" -> "").upsertAgents shouldBe Seq.empty
    }

    "build 'mongoEncryption' " in {

      configWith().mongoEncryption shouldBe MongoEncryption()

      configWith(
        "mongodb.encryption.enabled" -> "false"
      ).mongoEncryption shouldBe MongoEncryption()

      configWith(
        "mongodb.encryption.key" -> "ABC"
      ).mongoEncryption shouldBe MongoEncryption()

      configWith(
        "mongodb.encryption.enabled" -> "false",
        "mongodb.encryption.key" -> "ABC"
      ).mongoEncryption shouldBe MongoEncryption()


      configWith(
        "mongodb.encryption.enabled" -> "true",
        "mongodb.encryption.key" -> "ABC"
      ).mongoEncryption shouldBe MongoEncryption(enabled = true, key = Some("ABC"))

      val caught = intercept[RuntimeException] {
        configWith(
          "mongodb.encryption.enabled" -> "true"
        ).mongoEncryption
      }
      caught.getMessage shouldBe s"Could not find config key 'mongodb.encryption.key'"

    }

  }

}
