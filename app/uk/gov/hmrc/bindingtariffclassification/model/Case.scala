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

import java.time.ZonedDateTime

import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus

case class Case
(
  reference: String,
  status: CaseStatus,
  createdDate: ZonedDateTime = ZonedDateTime.now(),
  daysElapsed: Long = 0,
  closedDate: Option[ZonedDateTime] = None,
  caseBoardsFileNumber: Option[String] = None,
  assignee: Option[Operator] = None,
  queueId: Option[String] = None,
  application: Application,
  decision: Option[Decision] = None,
  attachments: Seq[Attachment] = Seq.empty,
  keywords: Set[String] = Set.empty
)

object CaseStatus extends Enumeration {
  type CaseStatus = Value
  val DRAFT, NEW, OPEN, SUPPRESSED, REFERRED, REJECTED, CANCELLED, SUSPENDED, COMPLETED, REVOKED, ANNULLED = Value
}

case class Status
(
  status: CaseStatus
)
