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

class UserSearchTest extends BaseSpec {

  private val search =
    UserSearch(role = Some(Set(Role.CLASSIFICATION_MANAGER)), team = Some("1"))

  private val params: Map[String, Seq[String]] =
    Map("role" -> Seq("CLASSIFICATION_MANAGER"), "member_of_teams" -> Seq("1"))

  private val emptyParams: Map[String, Seq[String]] =
    params.mapValues(_.map(_ => "")).toMap

  "Search Binder" should {

    "Unbind Unpopulated Search to Query String" in {
      UserSearch.bindable.unbind("", UserSearch()) shouldBe ""
    }

    "Unbind Populated Search to Query String" in {
      val populatedQueryParam: String =
        "role=CLASSIFICATION_MANAGER" +
          "&member_of_teams=1"
      URLDecoder.decode(UserSearch.bindable.unbind("", search), "UTF-8") shouldBe populatedQueryParam
    }

    "Bind empty query string" in {
      UserSearch.bindable.bind("", Map()) shouldBe Some(Right(UserSearch()))
    }

    "Bind query string with empty values" in {
      UserSearch.bindable.bind("", emptyParams) shouldBe Some(
        Right(UserSearch())
      )
    }

    "Bind populated query string" in {
      UserSearch.bindable.bind("", params) shouldBe Some(Right(search))
    }
  }

}
