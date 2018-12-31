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

package uk.gov.hmrc.bindingtariffclassification.config

import java.time.{LocalTime, ZoneId}

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.test.UnitSpec

class AppConfigTest extends UnitSpec with GuiceOneAppPerSuite {

  private def configWith(pairs: (String, String)*): AppConfig = {
    new AppConfig(Configuration.from(pairs.map(e => e._1 -> e._2).toMap), Environment.simple())
  }

  "Config" should {
    "build 'clock'" in {
      configWith().clock.getZone shouldBe ZoneId.systemDefault()
    }

    "build 'isDeleteEnabled" in {
      configWith("deleteEnabled" -> "true").isDeleteEnabled shouldBe true
      configWith("deleteEnabled" -> "false").isDeleteEnabled shouldBe false
    }

    "build 'DaysElapsedConfig" in {
      val config = configWith(
        "scheduler.days-elapsed.run-time" -> "00:00",
        "scheduler.days-elapsed.interval-days" -> "1"
      ).daysElapsed
      config.elapseTime shouldBe LocalTime.of(0, 0, 0)
      config.intervalDays shouldBe 1
    }

    "build 'bankHolidaysUrl without port" in {
      configWith(
        "microservice.services.bank-holidays.protocol" -> "https",
        "microservice.services.bank-holidays.host" -> "www.host.co.uk"
      ).bankHolidaysUrl shouldBe "https://www.host.co.uk"
    }

    "build 'bankHolidaysUrl with port" in {
      configWith(
        "microservice.services.bank-holidays.protocol" -> "https",
        "microservice.services.bank-holidays.host" -> "www.host.co.uk",
        "microservice.services.bank-holidays.port" -> "123"
      ).bankHolidaysUrl shouldBe "https://www.host.co.uk:123"
    }
  }

}
