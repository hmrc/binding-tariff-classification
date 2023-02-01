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

package uk.gov.hmrc.bindingtariffclassification.scheduler

import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.{Event, FieldChange}

import java.time.Instant
import scala.collection.immutable.SortedMap

class StatusTimeline(statusChanges: Seq[(Instant, CaseStatus)]) {
  lazy val timeline: SortedMap[Instant, CaseStatus] = SortedMap[Instant, CaseStatus](statusChanges: _*)

  def statusOn(date: Instant): Option[CaseStatus] =
    if (timeline.contains(date)) {
      timeline.get(date)
    } else {
      timeline.rangeUntil(date).lastOption.map(_._2)
    }
}

object StatusTimeline {
  def from(events: Seq[Event]): StatusTimeline = new StatusTimeline(
    events
      .filter(_.details.isInstanceOf[FieldChange[CaseStatus]])
      .map(event => (event.timestamp, event.details.asInstanceOf[FieldChange[CaseStatus]].to))
  )
}
