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

case class CaseSearch(
  filter: CaseFilter     = CaseFilter(),
  sort: Option[CaseSort] = None
)

object CaseSearch {

  implicit def bindable(
    implicit
    filterBinder: QueryStringBindable[CaseFilter],
    sortBinder: QueryStringBindable[CaseSort]
  ): QueryStringBindable[CaseSearch] = new QueryStringBindable[CaseSearch] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, CaseSearch]] = {

      val filter: Option[Either[String, CaseFilter]] = filterBinder.bind(key, params)

      val sort: Option[Either[String, CaseSort]] = sortBinder.bind(key, params)

      Some(
        Right(
          CaseSearch(
            filter.map(_.right.get).getOrElse(CaseFilter()),
            sort.map(_.right.get)
          )
        )
      )
    }

    override def unbind(key: String, search: CaseSearch): String =
      Seq[String](
        filterBinder.unbind(key, search.filter),
        search.sort.map(sortBinder.unbind(key, _)).getOrElse("")
      ).filter(_.trim.nonEmpty).mkString("&")

  }

}
