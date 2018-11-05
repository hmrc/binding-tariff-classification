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

package uk.gov.hmrc.bindingtariffclassification.repository

import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.search.CaseParamsFilter
import uk.gov.hmrc.play.test.UnitSpec

class JsonObjectMapperTest extends UnitSpec {

  private val jsonMapper = new JsonObjectMapper

  "from()" should {

    "convert to Json with fields status, queueId and assigneeId" in {

      val filter = CaseParamsFilter(
        queueId = Some("valid_queue"),
        assigneeId = Some("valid_assignee"),
        status = Some(Seq("S1", "S2"))
      )

      mapFrom(filter) shouldBe
        """{
          | "queueId": "valid_queue",
          | "assigneeId": "valid_assignee",
          | "status": {
          |   "$in": [ "S1", "S2" ]
          |  }
          |}
        """.stripMargin.replaceAll(" ", "").replaceAll("\n", "")
    }

    "convert to Json with fields queueId and assigneeId" in {

      val filter = CaseParamsFilter(
        queueId = Some("valid_queue"),
        assigneeId = Some("valid_assignee")
      )

      mapFrom(filter) shouldBe
        """{
          | "queueId": "valid_queue",
          | "assigneeId": "valid_assignee"
          |}
        """.stripMargin.replaceAll(" ", "").replaceAll("\n", "")
    }

    "convert to Json with fields queueId and assigneeId using `none` value " in {

      val filter = CaseParamsFilter(queueId = Some("none"), assigneeId = Some("none"))

      mapFrom(filter) shouldBe
        """{
          | "queueId": null,
          | "assigneeId": null
          |}
        """.stripMargin.replaceAll(" ", "").replaceAll("\n", "")

    }

    "convert to Json with no filters" in {
      mapFrom(CaseParamsFilter()) shouldBe "{}"
    }

  }

  "fromReference()" should {

    "convert to Json from a valid reference" in {

      val validRef = "valid_reference"

      jsonMapper.fromReference(validRef).toString() shouldBe
        s"""{
          | "reference": "$validRef"
          |}
        """.stripMargin.replaceAll(" ", "").replaceAll("\n", "")
    }

  }

  "fromReferenceAndStatus()" should {

    "convert to Json from a valid reference and status" in {

      val validRef = "valid_reference"
      val notAllowedStatus = CaseStatus.REFERRED

      jsonMapper.fromReferenceAndStatus(validRef, notAllowedStatus).toString() shouldBe
        s"""{
           | "reference": "$validRef",
           | "status": { "$$ne": "$notAllowedStatus" }
           |}
        """.stripMargin.replaceAll(" ", "").replaceAll("\n", "")
    }

  }

  "updateField()" should {

    "convert to Json" in {

      val fieldName = "employee"
      val fieldValue = "Alex"

      jsonMapper.updateField(fieldName, fieldValue).toString() shouldBe
        s"""{
           | "$$set": { "$fieldName": "$fieldValue" }
           |}
        """.stripMargin.replaceAll(" ", "").replaceAll("\n", "")

    }

  }

  private def mapFrom(filter: CaseParamsFilter): String = {
    jsonMapper.from(filter).toString()
  }

}
