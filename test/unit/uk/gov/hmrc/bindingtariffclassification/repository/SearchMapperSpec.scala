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

import java.time.Instant

import play.api.libs.json.{JsNull, Json}
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.search.{Filter, Sort}
import uk.gov.hmrc.bindingtariffclassification.sort.{SortDirection, SortField}
import uk.gov.hmrc.play.test.UnitSpec

class SearchMapperSpec extends UnitSpec {

  private val jsonMapper = new SearchMapper

  "filterBy " should {

    "convert to Json when all possible parameters are taken into account " in {

      val filter = Filter(
        queueId = Some("valid_queue"),
        assigneeId = Some("valid_assignee"),
        statuses = Some(Set(CaseStatus.NEW, CaseStatus.OPEN)),
        traderName = Some("trader_name"),
        minDecisionEnd = Some(Instant.EPOCH),
        commodityCode = Some(12345.toString),
        goodDescription = Some("strawberry"),
        keywords = Some(Set("MTB", "BIKE"))
      )

      jsonMapper.filterBy(filter) shouldBe Json.obj(
        "queueId" -> "valid_queue",
        "assignee.id" -> "valid_assignee",
        "status" -> Json.obj("$in" -> Json.arr("NEW", "OPEN")),
        "application.holder.businessName" -> "trader_name",
        "decision.effectiveEndDate" -> Json.obj("$gte" -> Json.obj("$date" -> 0)),
        "decision.bindingCommodityCode" -> Json.obj("$regex" -> "^12345\\d*"),
        "application.goodDescription" -> Json.obj("$regex" -> ".*strawberry.*", "$options" -> "i"),
        "keywords" -> Json.obj("$all" -> Json.arr("MTB", "BIKE"))
      )
    }

    "convert to Json when just the `queueId` param is taken into account " in {
      jsonMapper.filterBy(Filter(queueId = Some("valid_queue"))) shouldBe Json.obj("queueId" -> "valid_queue")
    }

    "convert to Json when just the `assigneeId` param is taken into account " in {
      jsonMapper.filterBy(Filter(assigneeId = Some("valid_assignee"))) shouldBe Json.obj("assignee.id" -> "valid_assignee")
    }

    "convert to Json when just the `status` param is taken into account " in {
      jsonMapper.filterBy(Filter(statuses = Some(Set(CaseStatus.NEW, CaseStatus.OPEN)))) shouldBe Json.obj(
        "status" -> Json.obj(
          "$in" -> Json.arr("NEW", "OPEN")
        )
      )
    }

    "convert to Json when just the `keywords` param is taken into account " in {
      jsonMapper.filterBy(Filter(keywords = Some(Set("BIKE", "MTB")))) shouldBe Json.obj(
        "keywords" -> Json.obj(
          "$all" -> Json.arr("BIKE", "MTB")
        )
      )
    }

    "convert to Json when just the `traderName` param is taken into account " in {
      jsonMapper.filterBy(Filter(traderName = Some("traderName"))) shouldBe Json.obj("application.holder.businessName" -> "traderName")
    }

    "convert to Json when just the `minDecisionEnd` param is taken into account " in {
      jsonMapper.filterBy(Filter(minDecisionEnd = Some(Instant.EPOCH))) shouldBe Json.obj("decision.effectiveEndDate" -> Json.obj("$gte" -> Json.obj("$date" -> 0)))
    }

    "convert to Json when just the `commodityCode` param is taken into account " in {
      val expectedResult = Json.obj("decision.bindingCommodityCode" -> Json.obj("$regex" -> "^1234\\d*"))
      jsonMapper.filterBy(Filter(commodityCode = Some("1234"))) shouldBe expectedResult
    }

    "convert to Json when just the `goodDescription` param is taken into account " in {
      val expectedResult = Json.obj("application.goodDescription" -> Json.obj("$regex" -> ".*strawberry.*", "$options" -> "i"))
      jsonMapper.filterBy(Filter(goodDescription = Some("strawberry"))) shouldBe expectedResult
    }

    "convert to Json when fields `queueId` and `assigneeId` are set to `none` " in {

      val filter = Filter(queueId = Some("none"), assigneeId = Some("none"))

      jsonMapper.filterBy(filter) shouldBe Json.obj(
        "queueId" -> JsNull,
        "assignee.id" -> JsNull
      )
    }

    "convert to Json when there are no filters" in {
      jsonMapper.filterBy(Filter()) shouldBe Json.obj()
    }

  }

  "SortBy " should {

    "sort by passed field and default direction to descending(-1)" in {

      val sort = Sort(
        field = SortField.COMMODITY_CODE,
        direction = SortDirection.DESCENDING
      )

      jsonMapper.sortBy(sort) shouldBe Json.obj("decision.bindingCommodityCode" -> -1)
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

      jsonMapper.reference(validRef) shouldBe Json.obj("reference" -> validRef)
    }

  }

  "fromReferenceAndStatus()" should {

    "convert to Json from a valid reference and status" in {

      val validRef = "valid_reference"
      val notAllowedStatus = CaseStatus.REFERRED

      jsonMapper.fromReferenceAndStatus(validRef, notAllowedStatus) shouldBe Json.obj(
        "reference" -> validRef,
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
