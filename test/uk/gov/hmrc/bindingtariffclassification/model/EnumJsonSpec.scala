/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.model

import play.api.libs.json.{Format, JsError, JsNumber, JsString, JsSuccess, Json}
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec

class EnumJsonSpec extends BaseSpec {

  object SampleStatusEnum extends Enumeration {
    val Active, Inactive, Pending = Value
  }

  "EnumJson.format" should {
    "serialize and deserialize enum values correctly" in {
      val format = EnumJson.format(SampleStatusEnum)
      format.writes(SampleStatusEnum.Active) shouldBe JsString("Active")
      format.reads(JsString("Inactive"))     shouldBe JsSuccess(SampleStatusEnum.Inactive)
    }

    "fail when reading non-string enum values" in {
      val format = EnumJson.format(SampleStatusEnum)
      format.reads(JsNumber(42)) shouldBe a[JsError]
    }

    "fail when reading unknown enum names" in {
      val format = EnumJson.format(SampleStatusEnum)
      format.reads(JsString("Unknown")) shouldBe a[JsError]
    }

    "EnumJson.readsMap should read Map[CaseStatus, Int] from JSON" in {
      given caseStatusFormat: Format[CaseStatus.Value] = EnumJson.format(CaseStatus)

      val json = Json.parse(
        """{
          |  "NEW": 1,
          |  "OPEN": 2
          |}""".stripMargin
      )

      val result = EnumJson.formatMap[CaseStatus.Value, Int].reads(json)

      result shouldBe JsSuccess(
        Map(
          CaseStatus.NEW  -> 1,
          CaseStatus.OPEN -> 2
        )
      )
    }

    "EnumJson.writesMap should write Map[CaseStatus, Int] as JSON" in {
      given caseStatusFormat: Format[CaseStatus.Value] = EnumJson.format(CaseStatus)

      val map = Map(
        CaseStatus.DRAFT    -> 5,
        CaseStatus.REJECTED -> 9
      )

      val json = EnumJson.formatMap[CaseStatus.Value, Int].writes(map)

      json shouldBe Json.obj(
        "DRAFT"    -> 5,
        "REJECTED" -> 9
      )
    }

  }
}
