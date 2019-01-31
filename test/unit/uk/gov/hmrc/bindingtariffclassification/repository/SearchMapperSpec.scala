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

package uk.gov.hmrc.bindingtariffclassification.repository

import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.search.{Filter, Sort}
import uk.gov.hmrc.bindingtariffclassification.model.sort.{SortDirection, SortField}
import uk.gov.hmrc.play.test.UnitSpec

class SearchMapperSpec extends UnitSpec {

  private val jsonMapper = new SearchMapper

  "filterBy " should {

    "convert to Json all possible fields in Field object" in {

      val filter = Filter(
        queueId = Some("valid_queue"),
        assigneeId = Some("valid_assignee"),
        status = Some("S1,S2"),
        reference = Some("valid_reference"),
        traderName = Some("traderName")
      )

      filterBy(filter) shouldBe
        """{
          | "reference": "valid_reference",
          | "application.holder.businessName" : "traderName",
          | "queueId": "valid_queue",
          | "assignee.id": "valid_assignee",
          | "status": {
          |   "$in": [ "S1", "S2" ]
          |  }
          |}
        """.stripMargin.replaceAll(" ", "").replaceAll("\n", "")
    }

    "convert to Json just queueId " in {
      filterBy(Filter(queueId = Some("valid_queue"))) shouldBe  """{"queueId":"valid_queue"}"""
    }

    "convert to Json just assigneeId " in {
      filterBy(Filter(assigneeId = Some("valid_assignee"))) shouldBe  """{"assignee.id":"valid_assignee"}"""
    }

    "convert to Json just status " in {
      filterBy(Filter(status = Some("S1,S2,S3"))) shouldBe """{"status":{"$in":["S1","S2","S3"]}}"""
    }

    "convert to Json just reference " in {
      filterBy(Filter(reference = Some("valid_reference"))) shouldBe  """{"reference":"valid_reference"}"""
    }

    "convert to Json just trader name " in {
      filterBy(Filter(traderName = Some("traderName"))) shouldBe  """{"application.holder.businessName":"traderName"}"""
    }

    "convert to Json with fields queueId and assigneeId using `none` value " in {

      val filter = Filter(queueId = Some("none"), assigneeId = Some("none"))

      filterBy(filter) shouldBe
        """{
          | "queueId": null,
          | "assignee.id": null
          |}
        """.stripMargin.replaceAll(" ", "").replaceAll("\n", "")

    }

    "convert to Json with no filters" in {
      filterBy(Filter()) shouldBe "{}"
    }

  }

  "SortBy " should {
     val sortedField = Some(SortField.DAYS_ELAPSED)

    " sort by passed field and default direction to descending(-1)" in {

      val sort = Sort(
        field = sortedField
      )

      sortBy(sort) shouldBe  """{"sorted_field":-1}"""
    }

    " sort by passed field and set direction ascending(1)" in {

      val sort = Sort(
        field = sortedField,
        direction = Some(SortDirection.ASCENDING)
      )

      sortBy(sort) shouldBe  """{"sorted_field":1}"""
    }

    "empty sort should return empty Json" in {
      sortBy(Sort()) shouldBe  """{}"""
    }
  }

    "fromReference()" should {

    "convert to Json from a valid reference" in {

      val validRef = "valid_reference"

      jsonMapper.reference(validRef).toString() shouldBe
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

  private def filterBy(filter: Filter): String = {
    jsonMapper.filterBy(filter).toString()
  }

  private def sortBy(sort: Sort): String = {
    jsonMapper.sortBy(sort).toString()
  }
}
