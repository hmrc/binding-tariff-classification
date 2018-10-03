package uk.gov.hmrc.bindingtariffclassification.model

import java.time.ZonedDateTime

import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.EventType.EventType


case class Event
(
  id: String,
  `type`: EventType,
  comment: String,
  details: Details,
  userId: String,
  timestamp: ZonedDateTime
)

sealed trait Details

case class AttachmentDetails
(
  url: String,
  mimeType: String
) extends Details

case class StatusChange
(
  from: CaseStatus,
  to: CaseStatus
)

object EventType extends Enumeration {
  type EventType = Value
  val ATTACHMENT, CREATE = Value
}

