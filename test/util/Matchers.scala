/*
 * Copyright 2018 HM Revenue & Customs
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

package util

import java.time.ZonedDateTime
import org.scalatest.matchers.{MatchResult, Matcher}

object Matchers {

  def roughlyBe(time: ZonedDateTime) = new RoughlyMatches(time)

  protected class RoughlyMatches(time: ZonedDateTime) extends Matcher[ZonedDateTime] {

    override def apply(d: ZonedDateTime): MatchResult = MatchResult(
      d.isBefore(time.plusMinutes(1)) && d.isAfter(time.minusMinutes(1)),
      s"date [$d] was not within a minute of [$time]",
      s"date [$d] was within a minute of [$time]"
    )
  }

}
