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

import java.time.{Clock, Instant, ZoneOffset}

import org.mockito.BDDMockito._
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.{JsNull, JsString, Json}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.sort.{CaseSortField, SortDirection}
import uk.gov.hmrc.play.test.UnitSpec

class CaseSearchMapperSpec extends UnitSpec with MockitoSugar {

  private val config = mock[AppConfig]

  private val jsonMapper = new SearchMapper(config)

  "Search Mapper" should {

    "filter by all fields " in {

      val filter = CaseFilter(
        reference = Some(Set("id1", "id2")),
        applicationType = Some(ApplicationType.BTI),
        queueId = Some("valid_queue"),
        eori = Some("eori-number"),
        assigneeId = Some("valid_assignee"),
        statuses = Some(Set(PseudoCaseStatus.NEW, PseudoCaseStatus.OPEN)),
        traderName = Some("trader_name"),
        minDecisionEnd = Some(Instant.EPOCH),
        commodityCode = Some(12345.toString),
        decisionDetails = Some("strawberry"),
        keywords = Some(Set("MTB", "BIKE"))
      )

      jsonMapper.filterBy(filter) shouldBe Json.obj(
        "reference" -> Json.obj("$in" -> Json.arr("id1", "id2")),
        "application.type" -> "BTI",
        "queueId" -> "valid_queue",
        "assignee.id" -> "valid_assignee",
        "status" -> Json.obj("$in" -> Json.arr("NEW", "OPEN")),
        "decision.effectiveEndDate" -> Json.obj("$gte" -> Json.obj("$date" -> 0)),
        "decision.bindingCommodityCode" -> Json.obj("$regex" -> "^12345\\d*"),
        "$and" -> Json.arr(
          Json.obj("$or" -> Json.arr(
            Json.obj("application.holder.businessName" -> Json.obj("$regex" -> ".*trader_name.*", "$options" -> "i")),
            Json.obj("application.traderName" -> Json.obj("$regex" -> ".*trader_name.*", "$options" -> "i"))
          )),
          Json.obj("$or" -> Json.arr(
            Json.obj("decision.goodsDescription" -> Json.obj("$regex" -> ".*strawberry.*", "$options" -> "i")),
            Json.obj("decision.methodCommercialDenomination" -> Json.obj("$regex" -> ".*strawberry.*", "$options" -> "i")),
            Json.obj("decision.justification" -> Json.obj("$regex" -> ".*strawberry.*", "$options" -> "i"))
          )),
          Json.obj("$or" -> Json.arr(
            Json.obj("application.holder.eori" -> JsString("eori-number")),
            Json.obj("application.agent.eoriDetails.eori" -> JsString("eori-number"))
          ))
        ),
        "keywords" -> Json.obj("$all" -> Json.arr("MTB", "BIKE"))
      )
    }

    "filter by 'reference'" in {
      jsonMapper.filterBy(CaseFilter(reference = Some(Set("id1", "id2")))) shouldBe Json.obj(
        "reference" -> Json.obj("$in" -> Json.arr("id1", "id2"))
      )
    }

    "filter by 'application type'" in {
      jsonMapper.filterBy(CaseFilter(applicationType = Some(ApplicationType.BTI))) shouldBe Json.obj(
        "application.type" -> "BTI"
      )
    }

    "filter by 'queue id'" in {
      jsonMapper.filterBy(CaseFilter(queueId = Some("valid_queue"))) shouldBe Json.obj(
        "queueId" -> "valid_queue"
      )
    }

    "filter by 'eori'" in {
      jsonMapper.filterBy(CaseFilter(eori = Some("eori-number"))) shouldBe Json.obj(
        "$or" -> Json.arr(
          Json.obj("application.holder.eori" -> JsString("eori-number")),
          Json.obj("application.agent.eoriDetails.eori" -> JsString("eori-number"))
        )
      )
    }

    "filter by 'assignee id'" in {
      jsonMapper.filterBy(CaseFilter(assigneeId = Some("valid_assignee"))) shouldBe Json.obj(
        "assignee.id" -> "valid_assignee"
      )
    }

    "filter by 'status' - with concrete statuses only" in {
      jsonMapper.filterBy(CaseFilter(statuses = Some(Set(PseudoCaseStatus.NEW, PseudoCaseStatus.OPEN)))) shouldBe Json.obj(
        "status" -> Json.obj(
          "$in" -> Json.arr("NEW", "OPEN")
        )
      )
    }

    "filter by 'status' - with pseudo statuses only" in {
      given(config.clock) willReturn Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

      jsonMapper.filterBy(CaseFilter(statuses = Some(Set(PseudoCaseStatus.LIVE, PseudoCaseStatus.EXPIRED)))) shouldBe Json.obj(
        "$or" -> Json.arr(
          Json.obj(
            "status" -> "COMPLETED",
            "decision.effectiveEndDate" -> Json.obj("$gte" -> Json.obj("$date" -> 0))
          ),
          Json.obj(
            "status" -> "COMPLETED",
            "decision.effectiveEndDate" -> Json.obj("$lte" -> Json.obj("$date" -> 0))
          )
        )
      )
    }

    "filter by 'status' - with status type mix" in {
      given(config.clock) willReturn Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

      val filter = jsonMapper.filterBy(CaseFilter(
        statuses = Some(Set(PseudoCaseStatus.NEW, PseudoCaseStatus.OPEN, PseudoCaseStatus.LIVE, PseudoCaseStatus.EXPIRED)))
      )

      filter shouldBe Json.obj(
        "$or" -> Json.arr(
          Json.obj(
            "status" -> "COMPLETED",
            "decision.effectiveEndDate" -> Json.obj("$gte" -> Json.obj("$date" -> 0))
          ),
          Json.obj(
            "status" -> "COMPLETED",
            "decision.effectiveEndDate" -> Json.obj("$lte" -> Json.obj("$date" -> 0))
          ),
          Json.obj(
            "status" -> Json.obj(
              "$in" -> Json.arr("NEW", "OPEN")
            )
          )
        )
      )
    }

    "filter by 'keywords'" in {
      jsonMapper.filterBy(CaseFilter(keywords = Some(Set("BIKE", "MTB")))) shouldBe Json.obj(
        "keywords" -> Json.obj(
          "$all" -> Json.arr("BIKE", "MTB")
        )
      )
    }

    "filter by 'trader name'" in {
      jsonMapper.filterBy(CaseFilter(traderName = Some("trader_name"))) shouldBe Json.obj(
        "$or" ->
          Json.arr(
            Json.obj("application.holder.businessName" -> Json.obj("$regex" -> ".*trader_name.*", "$options" -> "i")),
            Json.obj("application.traderName" -> Json.obj("$regex" -> ".*trader_name.*", "$options" -> "i"))
          )
      )
    }

    "filter by 'min decision end'" in {
      jsonMapper.filterBy(CaseFilter(minDecisionEnd = Some(Instant.EPOCH))) shouldBe Json.obj(
        "decision.effectiveEndDate" -> Json.obj("$gte" -> Json.obj("$date" -> 0))
      )
    }

    "filter by 'commodity code'" in {
      jsonMapper.filterBy(CaseFilter(commodityCode = Some("1234"))) shouldBe Json.obj(
        "decision.bindingCommodityCode" -> Json.obj("$regex" -> "^1234\\d*")
      )
    }

    "filter by 'decision details'" in {
      jsonMapper.filterBy(CaseFilter(decisionDetails = Some("strawberry"))) shouldBe Json.obj(
        "$or" ->
          Json.arr(
            Json.obj("decision.goodsDescription" -> Json.obj("$regex" -> ".*strawberry.*", "$options" -> "i")),
            Json.obj("decision.methodCommercialDenomination" -> Json.obj("$regex" -> ".*strawberry.*", "$options" -> "i")),
            Json.obj("decision.justification" -> Json.obj("$regex" -> ".*strawberry.*", "$options" -> "i"))
          )
      )
    }

    "filter fields with 'none' representing a missing field" in {
      val filter = CaseFilter(queueId = Some("none"), assigneeId = Some("none"))

      jsonMapper.filterBy(filter) shouldBe Json.obj(
        "queueId" -> JsNull,
        "assignee.id" -> JsNull
      )
    }

    "filter fields with 'some' representing a populated field " in {

      val filter = CaseFilter(queueId = Some("some"), assigneeId = Some("some"))

      jsonMapper.filterBy(filter) shouldBe Json.obj(
        "queueId" -> Json.obj("$ne" -> JsNull),
        "assignee.id" -> Json.obj("$ne" -> JsNull)
      )
    }

    "filter nothing" in {
      jsonMapper.filterBy(CaseFilter()) shouldBe Json.obj()
    }

  }

  "SortBy " should {

    "sort by passed field and default direction to descending(-1)" in {

      val sort = CaseSort(
        field = CaseSortField.COMMODITY_CODE,
        direction = SortDirection.DESCENDING
      )

      jsonMapper.sortBy(sort) shouldBe Json.obj("decision.bindingCommodityCode" -> -1)
    }

    "sort by passed field and set direction ascending(1)" in {

      val sort = CaseSort(
        field = CaseSortField.DAYS_ELAPSED,
        direction = SortDirection.ASCENDING
      )

      jsonMapper.sortBy(sort) shouldBe Json.obj("daysElapsed" -> 1)
    }

    "sort by decision start date and set direction descending" in {

      val sort = CaseSort(
        field = CaseSortField.DECISION_START_DATE,
        direction = SortDirection.ASCENDING
      )

      jsonMapper.sortBy(sort) shouldBe Json.obj("decision.effectiveStartDate" -> 1)
    }


    "sort by created date and set direction descending" in {

      val sort = CaseSort(
        field = CaseSortField.CREATED_DATE,
        direction = SortDirection.DESCENDING
      )

      jsonMapper.sortBy(sort) shouldBe Json.obj("createdDate" -> -1)
    }

    "sort by reference " in {

      val sort = CaseSort(
        field = CaseSortField.REFERENCE,
        direction = SortDirection.DESCENDING
      )

      jsonMapper.sortBy(sort) shouldBe Json.obj("reference" -> -1)
    }
  }

  "fromReference()" should {

    "convert to Json from a valid reference" in {
      val validRef = "valid_reference"

      jsonMapper.reference(validRef) shouldBe Json.obj("reference" -> validRef)
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
