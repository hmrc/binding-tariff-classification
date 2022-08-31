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

package uk.gov.hmrc.bindingtariffclassification.config

import play.api.Configuration
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec

import java.time.ZoneOffset

class AppConfigTest extends BaseSpec {

  private def configWith(pairs: (String, String)*): AppConfig =
    new AppConfig(Configuration.from(pairs.map(e => e._1 -> e._2).toMap), serviceConfig)

  "Config" should {

    "build 'clock'" in {
      configWith().clock.getZone shouldBe ZoneOffset.UTC
    }

    "build 'isTestMode'" in {
      configWith("testMode" -> "true").isTestMode  shouldBe true
      configWith("testMode" -> "false").isTestMode shouldBe false
    }

    "build 'ActiveDaysElapsedConfig'" in {
      val config = configWith(
        "scheduler.active-days-elapsed.enabled"  -> "true",
        "scheduler.active-days-elapsed.schedule" -> "0 0 3 * * ?"
      ).activeDaysElapsed

      config.enabled                    shouldBe true
      config.schedule.getCronExpression shouldBe "0 0 3 * * ?"
    }

    "build 'ReferredDaysElapsedConfig'" in {
      val config = configWith(
        "scheduler.referred-days-elapsed.enabled"  -> "true",
        "scheduler.referred-days-elapsed.schedule" -> "0 0 2 * * ?"
      ).referredDaysElapsed
      config.enabled                    shouldBe true
      config.schedule.getCronExpression shouldBe "0 0 2 * * ?"
    }

    "build 'fileStoreCleanupConfig'" in {
      val config = configWith(
        "scheduler.filestore-cleanup.enabled"  -> "true",
        "scheduler.filestore-cleanup.schedule" -> "0 0 1 ? * 1"
      ).fileStoreCleanup
      config.enabled                    shouldBe true
      config.schedule.getCronExpression shouldBe "0 0 1 ? * 1"
    }

    "build 'fileStoreUrl'" in {
      //take expected from application.conf
      configWith().fileStoreUrl shouldBe "http://localhost:9583"
    }

    "build 'bankHolidaysUrl'" in {
      //take expected from application.conf
      configWith().bankHolidaysUrl shouldBe "http://localhost:9587"
    }

    "build 'upsert-permitted-agents'" in {
      configWith("upsert-permitted-agents" -> "x,y").upsertAgents shouldBe Seq("x", "y")
      configWith("upsert-permitted-agents" -> "").upsertAgents    shouldBe Seq.empty
    }

    "build 'mongoEncryption' with default" in {
      configWith().mongoEncryption shouldBe MongoEncryption()
    }

    "build 'mongoEncryption' with false" in {
      configWith(
        "mongodb.encryption.enabled" -> "false"
      ).mongoEncryption shouldBe MongoEncryption()
    }

    "build 'mongoEncryption' with value not boolean" in {
      configWith(
        "mongodb.encryption.key" -> "ABC"
      ).mongoEncryption shouldBe MongoEncryption()
    }

    "build 'mongoEncryption' with value false and key ABC" in {
      configWith(
        "mongodb.encryption.enabled" -> "false",
        "mongodb.encryption.key"     -> "ABC"
      ).mongoEncryption shouldBe MongoEncryption()
    }

    "build 'mongoEncryption' with value true and key ABC" in {
      configWith(
        "mongodb.encryption.enabled" -> "true",
        "mongodb.encryption.key"     -> "ABC"
      ).mongoEncryption shouldBe MongoEncryption(enabled = true, key = Some("ABC"))
    }

    "build 'mongoEncryption' with value true and without key" in {
      val caught = intercept[RuntimeException] {
        configWith(
          "mongodb.encryption.enabled" -> "true"
        ).mongoEncryption
      }
      caught.getMessage shouldBe s"Could not find config key 'mongodb.encryption.key'"
    }

    "build 'case reference configuration" in {
      configWith("atar-case-reference-offset"  -> "10").atarCaseReferenceOffset  shouldBe 10
      configWith("other-case-reference-offset" -> "20").otherCaseReferenceOffset shouldBe 20
    }
  }
}
