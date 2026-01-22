/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.component

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsNull, JsSuccess, Json}
import uk.gov.hmrc.bindingtariffclassification.model.bta.BtaRulings
import BtaRulings.format

class BtaRulingsSpec extends AnyWordSpec with Matchers {

  "BtaRulings JSON Format" should {

    "successfully de-serialise from a valid JSON string" in {
      val jsonString = """{"total":10,"expiring":3}"""
      val expected   = BtaRulings(total = 10, expiring = 3)

      Json.parse(jsonString).validate[BtaRulings] shouldBe JsSuccess(expected)
    }

    "successfully round-trip: serialize and deserialize valid data" in {
      val model = BtaRulings(total = 20, expiring = 8)
      val json  = Json.toJson(model)

      json shouldBe Json.obj("total" -> 20, "expiring" -> 8)
      json.as[BtaRulings] shouldBe model
    }

    "fail to deserialize when fields are missing" in {
      val emptyJson = Json.obj()
      val result    = Json.fromJson[BtaRulings](emptyJson)

      result.isError shouldBe true
      result shouldBe a[JsError]
    }

    "fail to deserialize when types are incorrect" in {
      val badJson = Json.obj(
        "total" -> "not-a-number",
        "expiring" -> true
      )
      val result = BtaRulings.format.reads(badJson)

      result shouldBe a[JsError]
    }

    "handle null values by failing validation" in {
      val nullJson = Json.obj(
        "total" -> JsNull,
        "expiring" -> 0
      )

      Json.fromJson[BtaRulings](nullJson).isError shouldBe true
    }

    "successfully serialise to JSON (Covers the 'Apply' statement)" in {
      val model = BtaRulings(total = 10, expiring = 3)
      val json  = Json.toJson(model)

      json shouldBe Json.obj("total" -> 10, "expiring" -> 3)
    }

    "cover the Apply and Block statements" in {
      val jsonString = """{"total":10,"expiring":3}"""
      val expected = BtaRulings(total = 10, expiring = 3)
      val result = Json.parse(jsonString).validate[BtaRulings]
      result shouldBe JsSuccess(expected)

      val convertedJson = Json.toJson(expected)
      convertedJson shouldBe Json.obj("total" -> 10, "expiring" -> 3)
    }

    "cover the Failure Block paths" in {
      val invalidJson = Json.obj("total" -> "wrong")

      BtaRulings.format.reads(invalidJson).isError shouldBe true
    }
  }
}