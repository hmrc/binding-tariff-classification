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

import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.bindingtariffclassification.model.Role.Role

case class UserSearch(role: Option[Set[Role]] = None, team: Option[String] = None)

object UserSearch {

  private val roleKey = "role"
  private val teamKey = "member_of_teams"

  implicit def bindable(
    implicit
    stringBinder: QueryStringBindable[String]
  ): QueryStringBindable[UserSearch] =
    new QueryStringBindable[UserSearch] {

      private def bindRole(key: String): Option[Role] =
        Role.values.find(_.toString.equalsIgnoreCase(key))

      override def bind(
        key: String,
        requestParams: Map[String, Seq[String]]
      ): Option[Either[String, UserSearch]] = {
        import uk.gov.hmrc.bindingtariffclassification.model.utils.BinderUtil._
        implicit val rp: Map[String, Seq[String]] = requestParams

        Some(
          Right(
            UserSearch(
              role = params(roleKey).map(_.map(bindRole).filter(_.isDefined).map(_.get)),
              team = param(teamKey)
            )
          )
        )
      }

      override def unbind(key: String, filter: UserSearch): String =
        Seq(
          filter.role.map(_.map(r => stringBinder.unbind(roleKey, r.toString)).mkString("&")),
          filter.team.map(r => stringBinder.unbind(teamKey, r))
        ).filter(_.isDefined).map(_.get).mkString("&")
    }
}
