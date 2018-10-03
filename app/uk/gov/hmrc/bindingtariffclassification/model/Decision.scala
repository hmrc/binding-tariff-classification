package uk.gov.hmrc.bindingtariffclassification.model

import java.time.ZonedDateTime

case class Decision
(
  bindingCommodityCode: String,
  effectiveStartDate: ZonedDateTime,
  effectiveEndDate: ZonedDateTime,
  goodsDescription: String,
  keywords: Seq[String],
  methodSearch: String,
  methodCommercialDenomination: String,
  appeal: Appeal
)

case class Appeal
(
  reviewStatus: String,
  reviewResult: String
)
