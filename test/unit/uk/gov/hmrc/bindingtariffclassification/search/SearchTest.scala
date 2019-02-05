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

package uk.gov.hmrc.bindingtariffclassification.search

import java.net.URLDecoder
import java.time.Instant

import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.sort.{SortDirection, SortField}
import uk.gov.hmrc.play.test.UnitSpec

class SearchTest extends UnitSpec {

  private val sort = Sort(
    field = SortField.DAYS_ELAPSED,
    direction = SortDirection.DESCENDING
  )

  private val filter = Filter(
    traderName = Some("trader-name"),
    queueId = Some("queue-id"),
    assigneeId = Some("assignee-id"),
    statuses = Some(Set(CaseStatus.NEW, CaseStatus.OPEN)),
    minDecisionEnd = Some(Instant.EPOCH)
  )

  private val search = Search(filter = filter, sort = Some(sort))

  private val params: Map[String, Seq[String]] = Map(
    "trader_name" -> Seq("trader-name"),
    "queue_id" -> Seq("queue-id"),
    "assignee_id" -> Seq("assignee-id"),
    "status" -> Seq("NEW", "OPEN"),
    "sort_by" -> Seq("days-elapsed"),
    "sort_direction" -> Seq("desc"),
    "min_decision_end" -> Seq("1970-01-01T00:00:00Z")
  )

  /**
    * When we add fields to Search these tests shouldn't need changing, only the fields above.
    **/
  "Search Binder" should {

    "Unbind Unpopulated Search to Query String" in {
      Search.bindable.unbind("", Search()) shouldBe ""
    }

    "Unbind Populated Search to Query String" in {
      val populatedQueryParam: String =
        "queue_id=queue-id" +
          "&assignee_id=assignee-id" +
          "&status=NEW" +
          "&status=OPEN" +
          "&trader_name=trader-name" +
          "&min_decision_end=1970-01-01T00:00:00Z" +
          "&sort_by=days-elapsed" +
          "&sort_direction=desc"
      URLDecoder.decode(Search.bindable.unbind("", search), "UTF-8") shouldBe populatedQueryParam
    }

    "Bind empty query string" in {
      Search.bindable.bind("", Map()) shouldBe Some(Right(Search()))
    }

    "Bind populated query string" in {
      Search.bindable.bind("", params) shouldBe Some(Right(search))
    }

    "Bind query string with missing sort_by" in {
      Search.bindable.bind("", params.filterKeys(_ != "sort_by")) shouldBe Some(Right(Search(filter, None)))
    }
  }

  "Filter Binder" should {

    "Unbind Unpopulated Filter to Query String" in {
      Filter.bindable.unbind("", Filter()) shouldBe ""
    }

    "Unbind Populated Filter to Query String" in {
      val populatedQueryParam: String =
        "queue_id=queue-id" +
          "&assignee_id=assignee-id" +
          "&status=NEW" +
          "&status=OPEN" +
          "&trader_name=trader-name" +
          "&min_decision_end=1970-01-01T00:00:00Z"
      URLDecoder.decode(Filter.bindable.unbind("", filter), "UTF-8") shouldBe populatedQueryParam
    }

    "Bind empty query string" in {
      Filter.bindable.bind("", Map()) shouldBe Some(Right(Filter()))
    }

    "Bind populated query string" in {
      Filter.bindable.bind("", params) shouldBe Some(Right(filter))
    }
  }

  "Sort Binder" should {

    "Unbind Populated Sort to Query String" in {
      val populatedQueryParam: String = "sort_by=days-elapsed&sort_direction=desc"
      Sort.bindable.unbind("", sort) shouldBe populatedQueryParam
    }

    "Bind empty query string" in {
      Sort.bindable.bind("", Map()) shouldBe None
    }

    "Bind populated query string" in {
      Sort.bindable.bind("", params) shouldBe Some(Right(sort))
    }

    "Bind populated query string with missing sort_by" in {
      Sort.bindable.bind("", params.filterKeys(_ != "sort_by")) shouldBe None
    }
  }

}
