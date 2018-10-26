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

import uk.gov.hmrc.bindingtariffclassification.model.search.CaseParamsFilter
import uk.gov.hmrc.play.test.UnitSpec

class JsonObjectMapperTest extends UnitSpec {

  val test = new JsonObjectMapper

  "covert to Json queueId and assigneeId" in {

    val filter = CaseParamsFilter(queueId = Some("valid_queue"), assigneeId = Some("valid_assignee"))

    mapFrom(filter) shouldBe
      """{
        | "queueId": "valid_queue",
        | "assigneeId": "valid_assignee"
        |}
      """.stripMargin.replaceAll(" ", "").replaceAll("\n", "")
  }

  "covert to Json queueId and assigneeId with none value " in {

    val filter = CaseParamsFilter(queueId = Some("none"), assigneeId = Some("none"))

    mapFrom(filter) shouldBe
      """{
        | "queueId": null,
        | "assigneeId": null
        |}
      """.stripMargin.replaceAll(" ", "").replaceAll("\n", "")

  }

  "covert to Json with no filters" in {
    mapFrom(CaseParamsFilter()) shouldBe "{}"
  }

  "map reference with a valid reference returns valid json" in {
    val validRef = "valid_reference"

    test.fromReference(validRef).toString() shouldBe
      """{
        | "reference": "valid_reference"
        |}
      """.stripMargin.replaceAll(" ", "").replaceAll("\n", "")

  }

  private def mapFrom(filter: CaseParamsFilter): String = {
    test.from(filter).toString()
  }


}
