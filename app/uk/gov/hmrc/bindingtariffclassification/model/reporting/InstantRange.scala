/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.model.reporting

import java.time.Instant

import play.api.mvc.QueryStringBindable

case class InstantRange(
  min: Instant,
  max: Instant
)

object InstantRange {
  val allTime = InstantRange(Instant.MIN, Instant.MAX)

  implicit def bindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[InstantRange] =
    new QueryStringBindable[InstantRange] {

      private def min(key: String) = s"min_$key"

      private def max(key: String) = s"max_$key"

      override def bind(key: String, requestParams: Map[String, Seq[String]]): Option[Either[String, InstantRange]] = {
        import uk.gov.hmrc.bindingtariffclassification.model.utils.BinderUtil._
        implicit val rp: Map[String, Seq[String]] = requestParams

        val minValue: Instant = param(min(key)).flatMap(bindInstant).getOrElse(Instant.MIN)
        val maxValue: Instant = param(max(key)).flatMap(bindInstant).getOrElse(Instant.MAX)
        val range             = InstantRange(minValue, maxValue)

        if (range == InstantRange.allTime)
          None
        else
          Some(Right(range))
      }

      override def unbind(key: String, filter: InstantRange): String =
        Seq(
          stringBinder.unbind(min(key), filter.min.toString),
          stringBinder.unbind(max(key), filter.max.toString)
        ).mkString("&")
    }
}
