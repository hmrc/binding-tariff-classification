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

package uk.gov.hmrc.bindingtariffclassification.model.search

import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.bindingtariffclassification.model.sort.SortField.SortField
import uk.gov.hmrc.bindingtariffclassification.model.sort.SortDirection.SortDirection
import uk.gov.hmrc.bindingtariffclassification.model.sort.{SortField, SortDirection}


case class Search
(
  filter: Filter,
  sort: Sort
)

case class Filter
(
  queueId: Option[String] = None,
  assigneeId: Option[String] = None,
  status: Option[String] = None,
  reference: Option[String] = None,
  traderName: Option[String] = None
)

case class Sort
(
  field: Option[SortField] = None,
  direction: Option[SortDirection] = Some(SortDirection.DESCENDING)
)

object Sort {

  private val sortByKey = "sort_by"
  private val sortDirectionKey = "sort_direction"

  private def bindSortField(key: Option[String]): Option[SortField] = {
    key.map(s => SortField.values.find(_.toString == s).getOrElse(throw new IllegalArgumentException))
  }

  implicit def bindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Sort] = new QueryStringBindable[Sort] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Sort]] = {
      def param(name: String): Option[String] = stringBinder.bind(name, params).filter(_.isRight).map(_.right.get)

      Some(
        Right(
          Sort(
            field = bindSortField(param(sortByKey)),
            direction = param(sortDirectionKey) map {
              case s: String if (s == "asc") => SortDirection.ASCENDING
              case s: String if (s == "desc") => SortDirection.DESCENDING
              case _ => SortDirection.DESCENDING
            }
          )
        )
      )
    }

    override def unbind(key: String, query: Sort): String = {
      val bindings: Seq[Option[String]] = Seq(
        query.field.map(v =>
          stringBinder.unbind(
            sortByKey,
            v match {
              case s if (s == SortField.DAYS_ELAPSED) => SortField.DAYS_ELAPSED.toString
            }
          )
        ),
        query.direction.map(v =>
          stringBinder.unbind(
            sortDirectionKey,
            v match {
              case s if (s == SortDirection.ASCENDING) => "asc"
              case s if (s == SortDirection.DESCENDING) => "desc"
            }
          )
        )
      )
      bindings.filter(_.isDefined).map(_.get).mkString("&")
    }
  }
}

object Filter {

  private val referenceKey = "reference"
  private val traderNameKey = "traderName"
  private val queueIdKey = "queue_id"
  private val assigneeIdKey = "assignee_id"
  private val statusKey = "status"

  implicit def bindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Filter] = new QueryStringBindable[Filter] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Filter]] = {
      def param(name: String): Option[String] = stringBinder.bind(name, params).filter(_.isRight).map(_.right.get)

      Some(
        Right(
          Filter(queueId = param(queueIdKey),
            assigneeId = param(assigneeIdKey),
            status = param(statusKey),
            reference = param(referenceKey),
            traderName = param(traderNameKey)
          )
        )
      )
    }

    override def unbind(key: String, filter: Filter): String = {
      val bindings: Seq[Option[String]] = Seq(
        filter.queueId.map(v => stringBinder.unbind(queueIdKey, v)),
        filter.assigneeId.map(v => stringBinder.unbind(assigneeIdKey, v)),
        filter.status.map(v => stringBinder.unbind(statusKey, v)),
        filter.reference.map(v => stringBinder.unbind(referenceKey, v)),
        filter.traderName.map(v => stringBinder.unbind(traderNameKey, v))
      )
      bindings.filter(_.isDefined).map(_.get).mkString("&")
    }
  }
}

object Search {
  implicit def bindable(implicit filterBinder: QueryStringBindable[Filter],
                        sortBinder: QueryStringBindable[Sort]): QueryStringBindable[Search] = new QueryStringBindable[Search] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Search]] = {
      val filter: Option[Either[String, Filter]] = filterBinder.bind(key, params)
      val sort: Option[Either[String, Sort]] = sortBinder.bind(key, params)

      Some(
        Right(
          Search(
            filter.map(_.right.get).getOrElse(Filter()),
            sort.map(_.right.get).getOrElse(Sort())
          )
        )
      )
    }

    override def unbind(key: String, search: Search): String = {
      Seq(
        filterBinder.unbind(key, search.filter),
        sortBinder.unbind(key, search.sort)
      ).filter(_.trim.length > 0).mkString("&")
    }
  }

}
