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

import uk.gov.hmrc.bindingtariffclassification.model.ApplicationType.ApplicationType
import uk.gov.hmrc.bindingtariffclassification.model.LiabilityStatus.LiabilityStatus

sealed trait Application {
  val `type`: ApplicationType
}

case class BTIApplication
(
  offline: Boolean = false,
  holder: EORIDetails,
  contact: Contact,
  agent: Option[EORIDetails] = None,
  goodsDescription: String,
  confidentialInformation: Option[String] = None,
  otherInformation: Option[String] = None,
  reissuedBTIReference: Option[String] = None,
  relatedBTIReference: Option[String] = None,
  knownLegalProceedings: Option[String] = None,
  envisagesCommodityCode: Option[String] = None,
  sampleToBeProvided: Boolean = false,
  sampleToBeReturned: Boolean = false
) extends Application {
  override val `type` = ApplicationType.BTI
}

case class LiabilityOrder
(
  holder: EORIDetails,
  contact: Contact,
  status: LiabilityStatus,
  port: String,
  entryNumber: String,
  endDate: ZonedDateTime

) extends Application {
  override val `type` = ApplicationType.LIABILITY_ORDER
}

case class EORIDetails
(
  eori: String,
  traderName: String,
  addressLine1: String,
  addressLine2: String,
  addressLine3: String,
  postcode: String,
  country: String
)

case class Contact
(
  name: String,
  email: String,
  phone: String
)

object LiabilityStatus extends Enumeration {
  type LiabilityStatus = Value
  val LIVE, NON_LIVE = Value
}

object ApplicationType extends Enumeration {
  type ApplicationType = Value
  val BTI, LIABILITY_ORDER = Value
}