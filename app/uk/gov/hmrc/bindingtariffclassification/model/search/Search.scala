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

import java.time.Instant

import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.sort.SortDirection.SortDirection
import uk.gov.hmrc.bindingtariffclassification.model.sort.SortField.SortField
import uk.gov.hmrc.bindingtariffclassification.model.sort.{SortDirection, SortField}

import scala.util.Try


case class Search
(
  filter: Filter = Filter(),
  sort: Option[Sort] = None
)

case class Filter
(
  queueId: Option[String] = None,
  assigneeId: Option[String] = None,
  statuses: Option[Set[CaseStatus]] = None,
  traderName: Option[String] = None,
  minDecisionEnd: Option[Instant] = None
)

case class Sort
(
  field: SortField,
  direction: SortDirection = SortDirection.DESCENDING
)

object Sort {

  private val sortByKey = "sort_by"
  private val sortDirectionKey = "sort_direction"

  private def bindSortField(key: String): Option[SortField] = {
    SortField.values.find(_.toString == key)
  }

  private def bindSortDirection(key: String): Option[SortDirection] = {
    SortDirection.values.find(_.toString == key)
  }

  implicit def bindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Sort] = new QueryStringBindable[Sort] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Sort]] = {
      def param(name: String): Option[String] = stringBinder.bind(name, params).filter(_.isRight).map(_.right.get)

      val field: Option[SortField] = param(sortByKey).flatMap(bindSortField)
      val direction: Option[SortDirection] = param(sortDirectionKey).flatMap(bindSortDirection)
      (field, direction) match {
        case (Some(f), Some(d)) => Some(Right(Sort(field = f, direction = d)))
        case (Some(f), _) => Some(Right(Sort(field = f)))
        case _ => None
      }

    }

    override def unbind(key: String, query: Sort): String = {
      Seq[String](
        stringBinder.unbind(sortByKey, query.field.toString),
        stringBinder.unbind(sortDirectionKey, query.direction.toString)
      ).mkString("&")
    }
  }
}

object Filter {

  private val traderNameKey = "trader_name"
  private val queueIdKey = "queue_id"
  private val assigneeIdKey = "assignee_id"
  private val statusKey = "status"
  private val minDecisionEndKey = "min_decision_end"

  implicit def bindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Filter] = new QueryStringBindable[Filter] {

    private def bindCaseStatus(key: String): Option[CaseStatus] = {
      CaseStatus.values.find(_.toString == key)
    }

    private def bindInstant(key: String): Option[Instant] = Try(Instant.parse(key)).toOption

    override def bind(key: String, requestParams: Map[String, Seq[String]]): Option[Either[String, Filter]] = {
      def params(name: String): Option[Set[String]] = requestParams.get(name).map(_.flatMap(_.split(",")).toSet).filter(_.nonEmpty)
      def param(name: String): Option[String] = params(name).map(_.head)

      Some(Right(
        Filter(queueId = param(queueIdKey),
          assigneeId = param(assigneeIdKey),
          statuses = params(statusKey).map(_.map(bindCaseStatus).filter(_.isDefined).map(_.get)),
          traderName = param(traderNameKey),
          minDecisionEnd = param(minDecisionEndKey).flatMap(bindInstant)
        )
      ))
    }

    override def unbind(key: String, filter: Filter): String = {
      Seq(
        filter.queueId.map(v => stringBinder.unbind(queueIdKey, v)),
        filter.assigneeId.map(v => stringBinder.unbind(assigneeIdKey, v)),
        filter.statuses.map(_.map(v => stringBinder.unbind(statusKey, v.toString)).mkString("&")),
        filter.traderName.map(v => stringBinder.unbind(traderNameKey, v)),
        filter.minDecisionEnd.map(v => stringBinder.unbind(minDecisionEndKey, v.toString))
      ).filter(_.isDefined).map(_.get).mkString("&")
    }
  }
}

object Search {
  implicit def bindable(implicit filterBinder: QueryStringBindable[Filter],
                        sortBinder: QueryStringBindable[Sort]): QueryStringBindable[Search] = new QueryStringBindable[Search] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Search]] = {
      val filter: Option[Either[String, Filter]] = filterBinder.bind(key, params)
      val sort: Option[Either[String, Sort]] = sortBinder.bind(key, params)

      Some(Right(
        Search(
          filter.map(_.right.get).getOrElse(Filter()),
          sort.map(_.right.get)
        )
      ))
    }

    override def unbind(key: String, search: Search): String = {
      Seq[String](
        filterBinder.unbind(key, search.filter),
        search.sort.map(sortBinder.unbind(key, _)).getOrElse("")
      ).filter(_.trim.nonEmpty).mkString("&")
    }
  }

}
