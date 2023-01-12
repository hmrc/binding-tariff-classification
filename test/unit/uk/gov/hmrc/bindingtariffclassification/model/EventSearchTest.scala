/*
 * Copyright 2023 HM Revenue & Customs
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

import java.net.URLDecoder
import java.time.Instant

class EventSearchTest extends BaseSpec {

  private val search = EventSearch(
    caseReference = Some(Set("ref1", "ref2")),
    `type`        = Some(Set(EventType.NOTE, EventType.CASE_STATUS_CHANGE)),
    timestampMin  = Some(Instant.EPOCH),
    timestampMax  = Some(Instant.EPOCH.plusSeconds(1))
  )

  private val params: Map[String, Seq[String]] = Map(
    "case_reference" -> Seq("ref1", "ref2"),
    "type"           -> Seq("NOTE", "CASE_STATUS_CHANGE"),
    "min_timestamp"  -> Seq("1970-01-01T00:00:00Z"),
    "max_timestamp"  -> Seq("1970-01-01T00:00:01Z")
  )

  private val emptyParams: Map[String, Seq[String]] = params.mapValues(_.map(_ => ""))

  /** When we add fields to Search these tests shouldn't need changing, only the fields above.
    */
  "Search Binder" should {

    "Unbind Unpopulated Search to Query String" in {
      EventSearch.bindable.unbind("", EventSearch()) shouldBe ""
    }

    "Unbind Populated Search to Query String" in {
      val populatedQueryParam: String =
        "case_reference=ref1" +
          "&case_reference=ref2" +
          "&type=NOTE" +
          "&type=CASE_STATUS_CHANGE" +
          "&min_timestamp=1970-01-01T00:00:00Z" +
          "&max_timestamp=1970-01-01T00:00:01Z"
      URLDecoder.decode(EventSearch.bindable.unbind("", search), "UTF-8") shouldBe populatedQueryParam
    }

    "Bind empty query string" in {
      EventSearch.bindable.bind("", Map()) shouldBe Some(Right(EventSearch()))
    }

    "Bind query string with empty values" in {
      EventSearch.bindable.bind("", emptyParams) shouldBe Some(Right(EventSearch()))
    }

    "Bind populated query string" in {
      EventSearch.bindable.bind("", params) shouldBe Some(Right(search))
    }
  }

}
