/*
 * Copyright 2022 HM Revenue & Customs
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

import org.mockito.BDDMockito._
import org.mongodb.scala.model.Filters
import play.api.libs.json._
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.sort.{CaseSortField, SortDirection}
import uk.gov.hmrc.mongo.play.json.Codecs

import java.time.{Clock, Instant, ZoneOffset}

class CaseSearchMapperSpec extends BaseMongoIndexSpec {

  private val config = mock[AppConfig]

  private val jsonMapper = new SearchMapper(config)

  "Search Mapper" should {

    "filter by all fields " in {

      val filter = CaseFilter(
        reference        = Some(Set("id1", "id2")),
        applicationType  = Some(Set(ApplicationType.BTI, ApplicationType.LIABILITY_ORDER)),
        queueId          = Some(Set("valid_queue")),
        eori             = Some("eori-number"),
        assigneeId       = Some("valid_assignee"),
        statuses         = Some(Set(PseudoCaseStatus.NEW, PseudoCaseStatus.OPEN)),
        caseSource       = Some("case_source"),
        minDecisionStart = Some(Instant.EPOCH),
        minDecisionEnd   = Some(Instant.EPOCH),
        commodityCode    = Some(12345.toString),
        decisionDetails  = Some("strawberry"),
        keywords         = Some(Set("MTB", "BIKE"))
      )

      jsonMapper.filterBy(filter) shouldBe Json.obj(
        "reference"                     -> Json.obj("$in" -> Json.arr("id1", "id2")),
        "application.type"              -> Json.obj("$in" -> Json.arr("BTI", "LIABILITY_ORDER")),
        "queueId"                       -> Json.obj("$in" -> Json.arr("valid_queue")),
        "assignee.id"                   -> "valid_assignee",
        "status"                        -> Json.obj("$in" -> Json.arr("NEW", "OPEN")),
        "decision.effectiveStartDate"   -> Json.obj("$gte" -> Json.obj("$date" -> 0)),
        "decision.effectiveEndDate"     -> Json.obj("$gte" -> Json.obj("$date" -> 0)),
        "decision.bindingCommodityCode" -> Json.obj("$regex" -> "^12345\\d*"),
        "$and" -> Json.arr(
          Json.obj(
            "$or" -> Json.arr(
              Json.obj("application.holder.businessName" -> Json.obj("$regex" -> ".*case_source.*", "$options" -> "i")),
              Json.obj("application.traderName"          -> Json.obj("$regex" -> ".*case_source.*", "$options" -> "i")),
              Json.obj(
                "application.correspondenceStarter" -> Json.obj("$regex" -> ".*case_source.*", "$options" -> "i")
              ),
              Json.obj("application.contactName" -> Json.obj("$regex" -> ".*case_source.*", "$options" -> "i"))
            )
          ),
          Json.obj(
            "$or" -> Json.arr(
              Json.obj("decision.goodsDescription" -> Json.obj("$regex" -> ".*strawberry.*", "$options" -> "i")),
              Json.obj(
                "decision.methodCommercialDenomination" -> Json.obj("$regex" -> ".*strawberry.*", "$options" -> "i")
              ),
              Json.obj("decision.justification" -> Json.obj("$regex" -> ".*strawberry.*", "$options" -> "i"))
            )
          ),
          Json.obj(
            "$or" -> Json.arr(
              Json.obj("application.holder.eori"            -> JsString("eori-number")),
              Json.obj("application.agent.eoriDetails.eori" -> JsString("eori-number"))
            )
          )
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
      jsonMapper.filterBy(CaseFilter(applicationType = Some(Set(ApplicationType.BTI, ApplicationType.LIABILITY_ORDER)))) shouldBe Json
        .obj(
          "application.type" -> Json.obj("$in" -> Json.arr("BTI", "LIABILITY_ORDER"))
        )
    }

    "filter by 'queue id'" in {
      jsonMapper.filterBy(CaseFilter(queueId = Some(Set("valid_queue")))) shouldBe Json.obj(
        "queueId" -> Json.obj("$in" -> Json.arr("valid_queue"))
      )
    }

    "filter by 'eori'" in {
      jsonMapper.filterBy(CaseFilter(eori = Some("eori-number"))) shouldBe Json.obj(
        "$or" -> Json.arr(
          Json.obj("application.holder.eori"            -> JsString("eori-number")),
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
      jsonMapper.filterBy(CaseFilter(statuses = Some(Set(PseudoCaseStatus.NEW, PseudoCaseStatus.OPEN)))) shouldBe Json
        .obj(
          "status" -> Json.obj(
            "$in" -> Json.arr("NEW", "OPEN")
          )
        )
    }

    "filter by 'status' - with pseudo statuses only" in {
      given(config.clock) willReturn Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

      jsonMapper.filterBy(CaseFilter(statuses = Some(Set(PseudoCaseStatus.LIVE, PseudoCaseStatus.EXPIRED)))) shouldBe Json
        .obj(
          "$or" -> Json.arr(
            Json.obj(
              "status"                    -> "COMPLETED",
              "decision.effectiveEndDate" -> Json.obj("$gte" -> Json.obj("$date" -> 0))
            ),
            Json.obj(
              "status"                    -> "COMPLETED",
              "decision.effectiveEndDate" -> Json.obj("$lte" -> Json.obj("$date" -> 0))
            )
          )
        )
    }

    "filter by 'status' - with status type mix" in {
      given(config.clock) willReturn Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

      val filter = jsonMapper.filterBy(
        CaseFilter(
          statuses =
            Some(Set(PseudoCaseStatus.NEW, PseudoCaseStatus.OPEN, PseudoCaseStatus.LIVE, PseudoCaseStatus.EXPIRED))
        )
      )

      filter shouldBe Json.obj(
        "$or" -> Json.arr(
          Json.obj(
            "status"                    -> "COMPLETED",
            "decision.effectiveEndDate" -> Json.obj("$gte" -> Json.obj("$date" -> 0))
          ),
          Json.obj(
            "status"                    -> "COMPLETED",
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

    "filter by 'case source'" in {
      jsonMapper.filterBy(CaseFilter(caseSource = Some("case_source"))) shouldBe Json.obj(
        "$or" ->
          Json.arr(
            Json.obj("application.holder.businessName"   -> Json.obj("$regex" -> ".*case_source.*", "$options" -> "i")),
            Json.obj("application.traderName"            -> Json.obj("$regex" -> ".*case_source.*", "$options" -> "i")),
            Json.obj("application.correspondenceStarter" -> Json.obj("$regex" -> ".*case_source.*", "$options" -> "i")),
            Json.obj("application.contactName"           -> Json.obj("$regex" -> ".*case_source.*", "$options" -> "i"))
          )
      )
    }

    "filter by 'case details'" in {
      jsonMapper.filterBy(CaseFilter(caseDetails = Some("case_details"))) shouldBe Json.obj(
        "$or" ->
          Json.arr(
            Json.obj("application.goodName"            -> Json.obj("$regex" -> ".*case_details.*", "$options" -> "i")),
            Json.obj("application.summary"             -> Json.obj("$regex" -> ".*case_details.*", "$options" -> "i")),
            Json.obj("application.detailedDescription" -> Json.obj("$regex" -> ".*case_details.*", "$options" -> "i")),
            Json.obj("application.name"                -> Json.obj("$regex" -> ".*case_details.*", "$options" -> "i"))
          )
      )
    }

    "filter by 'min decision start'" in {
      jsonMapper.filterBy(CaseFilter(minDecisionStart = Some(Instant.EPOCH))) shouldBe Json.obj(
        "decision.effectiveStartDate" -> Json.obj("$gte" -> Json.obj("$date" -> 0))
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
            Json.obj(
              "decision.methodCommercialDenomination" -> Json.obj("$regex" -> ".*strawberry.*", "$options" -> "i")
            ),
            Json.obj("decision.justification" -> Json.obj("$regex" -> ".*strawberry.*", "$options" -> "i"))
          )
      )
    }

    "filter fields with 'none' representing a missing field" in {
      val filter = CaseFilter(queueId = Some(Set("none")), assigneeId = Some("none"))

      jsonMapper.filterBy(filter) shouldBe Json.obj(
        "queueId"     -> Json.obj("$in" -> JsArray(Seq(JsNull))),
        "assignee.id" -> JsNull
      )
    }

    "filter fields with 'some' representing a populated field " in {

      val filter = CaseFilter(queueId = Some(Set("some")), assigneeId = Some("some"))

      jsonMapper.filterBy(filter) shouldBe Json.obj(
        "queueId"     -> Json.obj("$ne" -> JsNull),
        "assignee.id" -> Json.obj("$ne" -> JsNull)
      )
    }

    "do not filter with 'none' and 'some'" in {
      val filter = CaseFilter(queueId = Some(Set("none", "some")), assigneeId = Some("none"))

      jsonMapper.filterBy(filter) shouldBe Json.obj(
        "assignee.id" -> JsNull
      )
    }

    "filter fields with 'none' and a queueId '5' representing gateway and queueId 5" in {
      val filter = CaseFilter(queueId = Some(Set("none", "5")), assigneeId = Some("none"))

      jsonMapper.filterBy(filter) shouldBe Json.obj(
        "queueId"     -> Json.obj("$in" -> JsArray(Seq(JsNull, JsString("5")))),
        "assignee.id" -> JsNull
      )
    }

    "filter fields with 'some' and a queueId '5' representing not null and queueId 5" in {
      val filter = CaseFilter(queueId = Some(Set("some", "5")), assigneeId = Some("none"))

      jsonMapper.filterBy(filter) shouldBe Json.obj(
        "queueId"     -> Json.obj("$ne" -> JsNull),
        "assignee.id" -> JsNull
      )
    }

    "filter nothing" in {
      jsonMapper.filterBy(CaseFilter()) shouldBe Json.obj()
    }
  }

  "SortBy " should {

    "sort by passed field and default direction to descending(-1)" in {

      val sort = CaseSort(
        field     = Set(CaseSortField.COMMODITY_CODE),
        direction = SortDirection.DESCENDING
      )

      jsonMapper.sortBy(sort) shouldBe Json.obj("decision.bindingCommodityCode" -> -1)
    }

    "sort by passed field and set direction ascending(1)" in {

      val sort = CaseSort(
        field     = Set(CaseSortField.DAYS_ELAPSED),
        direction = SortDirection.ASCENDING
      )

      jsonMapper.sortBy(sort) shouldBe Json.obj("daysElapsed" -> 1)
    }

    "sort by decision start date and set direction descending" in {

      val sort = CaseSort(
        field     = Set(CaseSortField.DECISION_START_DATE),
        direction = SortDirection.ASCENDING
      )

      jsonMapper.sortBy(sort) shouldBe Json.obj("decision.effectiveStartDate" -> 1)
    }

    "sort by created date and set direction descending" in {

      val sort = CaseSort(
        field     = Set(CaseSortField.CREATED_DATE),
        direction = SortDirection.DESCENDING
      )

      jsonMapper.sortBy(sort) shouldBe Json.obj("createdDate" -> -1)
    }

    "sort by reference " in {

      val sort = CaseSort(
        field     = Set(CaseSortField.REFERENCE),
        direction = SortDirection.DESCENDING
      )

      jsonMapper.sortBy(sort) shouldBe Json.obj("reference" -> -1)
    }

    "sort by case status and set direction ascending" in {

      val sort = CaseSort(
        field     = Set(CaseSortField.STATUS),
        direction = SortDirection.ASCENDING
      )

      jsonMapper.sortBy(sort) shouldBe Json.obj("status" -> 1)
    }

    "sort by case status and set direction descending" in {

      val sort = CaseSort(
        field     = Set(CaseSortField.STATUS),
        direction = SortDirection.DESCENDING
      )

      jsonMapper.sortBy(sort) shouldBe Json.obj("status" -> -1)
    }
  }

  "fromReference()" should {

    "convert to Json from a valid reference" in {
      val validRef = "valid_reference"

      jsonMapper.reference(validRef) shouldBe Filters.equal("reference", validRef)
    }

  }

  "updateField()" should {

    "convert to Json" in {

      val fieldName  = "employee"
      val fieldValue = "Alex"

      jsonMapper.updateField(fieldName, fieldValue) shouldBe Codecs.toBson(
        Json.obj(
          "$set" -> Json.obj("employee" -> "Alex")
        )
      )

    }

  }
}
