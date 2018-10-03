package uk.gov.hmrc.bindingtariffclassification.model


import java.time.ZonedDateTime

import uk.gov.hmrc.bindingtariffclassification.model.LiabilityType.LiabilityType

abstract class Application
(
  holder: EORIDetails,
  contact: Contact
)

case class BTIApplication
(
  holder: EORIDetails,
  contact: Contact,
  agent: EORIDetails,
  goodsDescription: String,
  confidentialInformation: String,
  otherInformation: String,
  reissuedBTIReference: String,
  relatedBTIReference: String,
  knownLegalProceedings: String,
  envisagesCommodityCode: String,
  sampleToBeProvided: Boolean,
  sampleToBeReturned: Boolean,
  fastTrackBTI: Boolean
) extends Application(holder, contact)

case class LiabilityApplication
(
  holder: EORIDetails,
  contact: Contact,
  liabilityType: LiabilityType,
  liabilityPort: String,
  liabilityEntryNumber: String,
  liabilityEndDate: ZonedDateTime
) extends Application(holder, contact)

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

case class Contact // Verify if this belongs under Holder and/or agent
(
  name: String,
  email: String,
  phone: String
)

object LiabilityType extends Enumeration {
  type LiabilityType = Value
  val LIVE, NON_LIVE = Value
}