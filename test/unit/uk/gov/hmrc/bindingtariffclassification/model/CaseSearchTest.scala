/*
 * Copyright 2021 HM Revenue & Customs
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

import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.sort.{CaseSortField, SortDirection}

import java.net.URLDecoder
import java.time.Instant

class CaseSearchTest extends BaseSpec {

  private val sort = CaseSort(
    field     = Set(CaseSortField.DAYS_ELAPSED),
    direction = SortDirection.DESCENDING
  )

  private val filter = CaseFilter(
    reference       = Some(Set("id1", "id2")),
    applicationType = Some(Set(ApplicationType.BTI, ApplicationType.LIABILITY_ORDER)),
    caseSource      = Some("trader-name"),
    queueId         = Some(Set("queue-id")),
    eori            = Some("eori-number"),
    assigneeId      = Some("assignee-id"),
    statuses        = Some(Set(PseudoCaseStatus.NEW, PseudoCaseStatus.OPEN)),
    minDecisionEnd  = Some(Instant.EPOCH),
    keywords        = Some(Set("BIKE", "MTB")),
    decisionDetails = Some("decision-details")
  )

  private val search = CaseSearch(filter = filter, sort = Some(sort))

  private val params: Map[String, Seq[String]] = Map(
    "reference"        -> Seq("id1", "id2"),
    "application_type" -> Seq("BTI", "LIABILITY_ORDER"),
    "case_source"      -> Seq("trader-name"),
    "queue_id"         -> Seq("queue-id"),
    "eori"             -> Seq("eori-number"),
    "assignee_id"      -> Seq("assignee-id"),
    "status"           -> Seq("NEW", "OPEN"),
    "min_decision_end" -> Seq("1970-01-01T00:00:00Z"),
    "decision_details" -> Seq("decision-details"),
    "keyword"          -> Seq("bike", "MTB"),
    "sort_by"          -> Seq("days-elapsed"),
    "sort_direction"   -> Seq("desc")
  )

  private val emptyParams: Map[String, Seq[String]] = Map(
    "reference"        -> Seq(""),
    "application_type" -> Seq(""),
    "case_source"      -> Seq(""),
    "queue_id"         -> Seq(""),
    "eori"             -> Seq(""),
    "assignee_id"      -> Seq(""),
    "status"           -> Seq(""),
    "min_decision_end" -> Seq(""),
    "keyword"          -> Seq(""),
    "sort_by"          -> Seq(""),
    "sort_direction"   -> Seq("")
  )

  /**
    * When we add fields to Search these tests shouldn't need changing, only the fields above.
    **/
  "Search Binder" should {

    "Unbind Unpopulated Search to Query String" in {
      CaseSearch.bindable.unbind("", CaseSearch()) shouldBe ""
    }

    "Unbind Populated Search to Query String" in {
      val populatedQueryParam: String =
        "reference=id1" +
          "&reference=id2" +
          "&application_type=BTI" +
          "&application_type=LIABILITY_ORDER" +
          "&queue_id=queue-id" +
          "&eori=eori-number" +
          "&assignee_id=assignee-id" +
          "&status=NEW" +
          "&status=OPEN" +
          "&case_source=trader-name" +
          "&min_decision_end=1970-01-01T00:00:00Z" +
          "&decision_details=decision-details" +
          "&keyword=BIKE" +
          "&keyword=MTB" +
          "&sort_by=days-elapsed" +
          "&sort_direction=desc"
      URLDecoder.decode(CaseSearch.bindable.unbind("", search), "UTF-8") shouldBe populatedQueryParam
    }

    "Bind empty query string" in {
      CaseSearch.bindable.bind("", Map()) shouldBe Some(Right(CaseSearch()))
    }

    "Bind query string with empty values" in {
      CaseSearch.bindable.bind("", emptyParams) shouldBe Some(Right(CaseSearch()))
    }

    "Bind populated query string" in {
      CaseSearch.bindable.bind("", params) shouldBe Some(Right(search))
    }

    "Bind query string with missing sort_by" in {
      CaseSearch.bindable.bind("", params.filterKeys(_ != "sort_by")) shouldBe Some(Right(CaseSearch(filter, None)))
    }
  }

  "Filter Binder" should {

    "Unbind Unpopulated Filter to Query String" in {
      CaseFilter.bindable.unbind("", CaseFilter()) shouldBe ""
    }

    "Unbind Populated Filter to Query String" in {
      val populatedQueryParam: String =
        "reference=id1" +
          "&reference=id2" +
          "&application_type=BTI" +
          "&application_type=LIABILITY_ORDER" +
          "&queue_id=queue-id" +
          "&eori=eori-number" +
          "&assignee_id=assignee-id" +
          "&status=NEW" +
          "&status=OPEN" +
          "&case_source=trader-name" +
          "&min_decision_end=1970-01-01T00:00:00Z" +
          "&decision_details=decision-details" +
          "&keyword=BIKE" +
          "&keyword=MTB"
      URLDecoder.decode(CaseFilter.bindable.unbind("", filter), "UTF-8") shouldBe populatedQueryParam
    }

    "Bind empty query string" in {
      CaseFilter.bindable.bind("", Map()) shouldBe Some(Right(CaseFilter()))
    }

    "Bind query string with empty values" in {
      CaseFilter.bindable.bind("", emptyParams) shouldBe Some(Right(CaseFilter()))
    }

    "Bind populated query string" in {
      CaseFilter.bindable.bind("", params) shouldBe Some(Right(filter))
    }
  }

  "Sort Binder" should {

    "Unbind Populated Sort to Query String" in {
      val populatedQueryParam: String = "sort_by=days-elapsed&sort_direction=desc"
      CaseSort.bindable.unbind("", sort) shouldBe populatedQueryParam
    }

    "Bind empty query string" in {
      CaseSort.bindable.bind("", Map()) shouldBe None
    }

    "Bind query string with empty values" in {
      CaseSort.bindable.bind("", emptyParams) shouldBe None
    }

    "Bind populated query string" in {
      CaseSort.bindable.bind("", params) shouldBe Some(Right(sort))
    }

    "Bind populated query string with missing sort_by" in {
      CaseSort.bindable.bind("", params.filterKeys(_ != "sort_by")) shouldBe None
    }
  }

}
