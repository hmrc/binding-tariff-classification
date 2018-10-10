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

package uk.gov.hmrc.bindingtariffclassification.model

import java.time.ZonedDateTime

import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.EventType.EventType


case class Event
(
  id: String,
  details: Details,
  userId: String,
  caseReference: String,
  timestamp: ZonedDateTime = ZonedDateTime.now()
)

sealed trait Details {
  val `type`: EventType
  val comment: Option[String]
}

case class Attachment
(
  // TODO: attachments need to be moved under `Case`
  // we need a boolean flag for identifying attachments sent by the trader in the BTI application
  // we need a boolean flag for identifying attachments visible in the public rulings UI
  // we need endpoints (POST, PUT, DELETE) for managing attachments
  url: String,
  mimeType: String,
  override val comment: Option[String] = None

) extends Details {
  override val `type` = EventType.ATTACHMENT
}

case class CaseStatusChange
(
  from: CaseStatus,
  to: CaseStatus,
  override val comment: Option[String] = None

) extends Details {
  override val `type` = EventType.CASE_STATUS_CHANGE
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
  val ATTACHMENT, CASE_STATUS_CHANGE, NOTE = Value
}
