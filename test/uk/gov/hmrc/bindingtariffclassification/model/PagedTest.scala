/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec

class PagedTest extends BaseSpec {

  "Paged" should {
    "map" in {
      Paged(Seq("hello")).map(_.toUpperCase) shouldBe Paged(Seq("HELLO"))
    }

    "calculate size" in {
      Paged.empty.size    shouldBe 0
      Paged(Seq("")).size shouldBe 1
    }

    "serialize to JSON" in {
      Json.toJson(Paged(Seq("Hello"), 1, 2, 3)).as[JsObject] shouldBe Json.obj(
        "results"     -> Json.arr("Hello"),
        "pageIndex"   -> 1,
        "pageSize"    -> 2,
        "resultCount" -> 3
      )
    }

    "serialize from JSON" in {
      Json
        .obj(
          "results"     -> Json.arr("Hello"),
          "pageIndex"   -> 1,
          "pageSize"    -> 2,
          "resultCount" -> 3
        )
        .as[Paged[String]] shouldBe Paged(Seq("Hello"), 1, 2, 3)
    }

    "calculate has next page" in {
      Paged.empty.hasNextPage                                                    shouldBe false
      Paged(Seq.empty, pageIndex = 1, pageSize = 1, resultCount = 1).hasNextPage shouldBe false
      Paged(Seq.empty, pageIndex = 1, pageSize = 1, resultCount = 2).hasNextPage shouldBe true
      Paged(Seq.empty, pageIndex = 1, pageSize = 1, resultCount = 2).hasNextPage shouldBe true
      Paged(Seq.empty, pageIndex = 2, pageSize = 1, resultCount = 2).hasNextPage shouldBe false
    }
  }

}
