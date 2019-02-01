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

import play.api.libs.json.{JsNull, Json}
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
        traderName = Some("trader_name")
      )

      jsonMapper.filterBy(filter) shouldBe Json.obj(
        "queueId" -> "valid_queue",
        "assignee.id" -> "valid_assignee",
        "status" -> Json.obj("$in" -> Json.arr("S1", "S2")),
        "application.holder.businessName" -> "trader_name"
      )
    }

    "convert to Json just queueId " in {
      jsonMapper.filterBy(Filter(queueId = Some("valid_queue"))) shouldBe Json.obj("queueId" -> "valid_queue")
    }

    "convert to Json just assigneeId " in {
      jsonMapper.filterBy(Filter(assigneeId = Some("valid_assignee"))) shouldBe Json.obj("assignee.id" -> "valid_assignee")
    }

    "convert to Json just status " in {
      jsonMapper.filterBy(Filter(status = Some("S1,S2,S3"))) shouldBe Json.obj(
        "status" -> Json.obj(
          "$in" -> Json.arr("S1", "S2", "S3")
        )
      )
    }

    "convert to Json just trader name " in {
      jsonMapper.filterBy(Filter(traderName = Some("traderName"))) shouldBe Json.obj("application.holder.businessName" -> "traderName")
    }

    "convert to Json with fields queueId and assigneeId using `none` value " in {

      val filter = Filter(queueId = Some("none"), assigneeId = Some("none"))

      jsonMapper.filterBy(filter) shouldBe Json.obj(
        "queueId" -> JsNull,
        "assignee.id" -> JsNull
      )
    }

    "convert to Json with no filters" in {
      jsonMapper.filterBy(Filter()) shouldBe Json.obj()
    }

  }

  "SortBy " should {

    "sort by passed field and default direction to descending(-1)" in {

      val sort = Sort(
        field = SortField.DAYS_ELAPSED,
        direction = SortDirection.DESCENDING
      )

      jsonMapper.sortBy(sort) shouldBe Json.obj("daysElapsed" -> -1)
    }

    "sort by passed field and set direction ascending(1)" in {

      val sort = Sort(
        field = SortField.DAYS_ELAPSED,
        direction = SortDirection.ASCENDING
      )

      jsonMapper.sortBy(sort) shouldBe Json.obj("daysElapsed" -> 1)
    }
  }

  "fromReference()" should {

    "convert to Json from a valid reference" in {
      val validRef = "valid_reference"

      jsonMapper.reference(validRef) shouldBe
        Json.obj(
          "reference" -> "valid_reference"
        )
    }

  }

  "fromReferenceAndStatus()" should {

    "convert to Json from a valid reference and status" in {

      val validRef = "valid_reference"
      val notAllowedStatus = CaseStatus.REFERRED

      jsonMapper.fromReferenceAndStatus(validRef, notAllowedStatus) shouldBe Json.obj(
        "reference" -> "valid_reference",
        "status" -> Json.obj("$ne" -> "REFERRED")
      )
    }

  }

  "updateField()" should {

    "convert to Json" in {

      val fieldName = "employee"
      val fieldValue = "Alex"

      jsonMapper.updateField(fieldName, fieldValue) shouldBe Json.obj(
        "$set" -> Json.obj("employee" -> "Alex")
      )

    }

  }
}
