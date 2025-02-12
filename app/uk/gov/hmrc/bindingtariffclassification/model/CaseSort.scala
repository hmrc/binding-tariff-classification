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

import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.bindingtariffclassification.sort.CaseSortField.CaseSortField
import uk.gov.hmrc.bindingtariffclassification.sort.SortDirection
import uk.gov.hmrc.bindingtariffclassification.sort.SortDirection.SortDirection

case class CaseSort(
  field: Set[CaseSortField],
  direction: SortDirection = SortDirection.ASCENDING
)

object CaseSort {

  private val sortByKey        = "sort_by"
  private val sortDirectionKey = "sort_direction"

  implicit def bindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[CaseSort] =
    new QueryStringBindable[CaseSort] {

      override def bind(key: String, requestParams: Map[String, Seq[String]]): Option[Either[String, CaseSort]] = {
        import uk.gov.hmrc.bindingtariffclassification.model.utils.BinderUtil._
        implicit val rp: Map[String, Seq[String]] = requestParams

        val field: Option[Set[CaseSortField]] =
          params(sortByKey).map(_.map(bindSortField).filter(_.isDefined).map(_.get))
        val direction: Option[SortDirection] = param(sortDirectionKey).flatMap(bindSortDirection)

        (field, direction) match {
          case (Some(f), Some(d)) => Some(Right(CaseSort(field = f, direction = d)))
          case (Some(f), _)       => Some(Right(CaseSort(field = f)))
          case _                  => None
        }
      }

      override def unbind(key: String, query: CaseSort): String =
        Seq[String](
          query.field.map(value => stringBinder.unbind(sortByKey, value.toString)).mkString("&"),
          stringBinder.unbind(sortDirectionKey, query.direction.toString)
        ).mkString("&")

    }

}
