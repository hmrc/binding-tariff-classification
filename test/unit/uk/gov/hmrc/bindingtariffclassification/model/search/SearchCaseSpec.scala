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

package uk.gov.hmrc.bindingtariffclassification.model.search


import uk.gov.hmrc.play.test.UnitSpec

class SearchCaseSpec extends UnitSpec {


  "searchCase" should {

    "covert to Json queueId and assigneeId" in {

      val expected =
        """{
          | "queueId": "valid_queue",
          | "assigneeId": "valid_assignee"
          |}
        """.stripMargin.replaceAll(" ", "").replaceAll("\n", "")

      val actual = SearchCase(queueId = Some("valid_queue"), assigneeId = Some("valid_assignee")).buildJson

      actual.toString() shouldBe expected
    }

    "covert to Json queueId and assigneeId with none value " in {
      val none_str = "none"
      val expected =
        """{
          | "queueId": null,
          | "assigneeId": null
          |}
        """.stripMargin.replaceAll(" ", "").replaceAll("\n", "")

      val actual = SearchCase(queueId = Some(none_str), assigneeId = Some(none_str)).buildJson

      actual.toString() shouldBe expected
    }

    "by reference should return json with the reference" in {

      val actual = SearchCase(reference = Some("valid_ref")).buildJson

      actual.toString() shouldBe
        """{
          | "reference": "valid_ref"
          |}
        """.stripMargin.replaceAll(" ", "").replaceAll("\n", "")

    }

    "covert to Json with no filters" in {

      val actual = SearchCase().buildJson

      actual.toString() shouldBe "{}"
    }

  }

}
