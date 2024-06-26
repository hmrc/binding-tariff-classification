/*
 * Copyright 2024 HM Revenue & Customs
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

case class Decision(
  bindingCommodityCode: String,
  effectiveStartDate: Option[Instant] = None,
  effectiveEndDate: Option[Instant] = None,
  justification: String,
  goodsDescription: String,
  methodSearch: Option[String] = None,
  methodCommercialDenomination: Option[String] = None,
  methodExclusion: Option[String] = None,
  appeal: Seq[Appeal] = Seq.empty,
  cancellation: Option[Cancellation] = None,
  explanation: Option[String] = None,
  decisionPdf: Option[Attachment] = None,
  letterPdf: Option[Attachment] = None
)
