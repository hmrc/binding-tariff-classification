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

package uk.gov.hmrc.bindingtariffclassification.model

import java.time.Instant

import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.bindingtariffclassification.model.EventType.EventType

case class EventSearch
(
  caseReference: Option[Set[String]] = None,
  `type`: Option[Set[EventType]] = None,
  timestampMin: Option[Instant] = None,
  timestampMax: Option[Instant] = None
)

object EventSearch {
  private val caseReferenceKey = "case_reference"
  private val typeKey = "type"
  private val timestampMinKey = "min_timestamp"
  private val timestampMaxKey = "max_timestamp"

  implicit def bindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[EventSearch] = new QueryStringBindable[EventSearch] {

    private def bindEventType(key: String): Option[EventType] = {
      EventType.values.find(_.toString.equalsIgnoreCase(key))
    }

    override def bind(key: String, requestParams: Map[String, Seq[String]]): Option[Either[String, EventSearch]] = {
      import uk.gov.hmrc.bindingtariffclassification.model.utils.BinderUtil._
      implicit val rp: Map[String, Seq[String]] = requestParams

      Some(
        Right(
          EventSearch(
            caseReference = params(caseReferenceKey),
            `type` = params(typeKey).map(_.map(bindEventType).filter(_.isDefined).map(_.get)),
            timestampMin = param(timestampMinKey).flatMap(bindInstant),
            timestampMax = param(timestampMaxKey).flatMap(bindInstant)
          )
        )
      )
    }

    override def unbind(key: String, filter: EventSearch): String = {
      Seq(
        filter.caseReference.map(_.map(s => stringBinder.unbind(caseReferenceKey, s.toString)).mkString("&")),
        filter.`type`.map(_.map(s => stringBinder.unbind(typeKey, s.toString)).mkString("&")),
        filter.timestampMin.map(i => stringBinder.unbind(timestampMinKey, i.toString)),
        filter.timestampMax.map(i => stringBinder.unbind(timestampMaxKey, i.toString))
      ).filter(_.isDefined).map(_.get).mkString("&")
    }
  }
}
