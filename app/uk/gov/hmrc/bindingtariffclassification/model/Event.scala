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
import java.util.UUID

import uk.gov.hmrc.bindingtariffclassification.model.AppealStatus.AppealStatus
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.EventType.EventType
import uk.gov.hmrc.bindingtariffclassification.model.ReviewStatus.ReviewStatus

case class Event
(
  id: String = UUID.randomUUID().toString,
  details: Details,
  operator: Operator,
  caseReference: String,
  timestamp: Instant
)

sealed trait Details {
  val `type`: EventType
  val comment: Option[String]
}

case class CaseStatusChange
(
  from: CaseStatus,
  to: CaseStatus,
  override val comment: Option[String] = None

) extends Details {
  override val `type` = EventType.CASE_STATUS_CHANGE
}

case class AppealStatusChange
(
  from: Option[AppealStatus],
  to: Option[AppealStatus],
  override val comment: Option[String] = None

) extends Details {
  override val `type`: EventType.Value = EventType.APPEAL_STATUS_CHANGE
}

case class ReviewStatusChange
(
  from: Option[ReviewStatus],
  to: Option[ReviewStatus],
  override val comment: Option[String] = None

) extends Details {
  override val `type`: EventType.Value = EventType.REVIEW_STATUS_CHANGE
}

case class Note
(
  override val comment: Option[String]

) extends Details {
  override val `type` = EventType.NOTE
  // TODO: the `comment` option should be always be defined - to be checked on the frontend
}


object EventType extends Enumeration {
  type EventType = Value
  val CASE_STATUS_CHANGE, APPEAL_STATUS_CHANGE, REVIEW_STATUS_CHANGE, NOTE = Value
}
