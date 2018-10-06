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

package uk.gov.hmrc.bindingtariffclassification.todelete

import java.time.ZonedDateTime

import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.utils.RandomGenerator

object CaseData {

  def createBTIApplication: BTIApplication = {
    BTIApplication(
      holder = createEORIDetails(RandomGenerator.randomUUID()),
      contact = Contact("", "", ""),
      agent = None,
      "",
      "",
      "",
      "",
      "",
      "",
      "")
  }

  def createOfflineBTI: BTIOfflineApplication = {
    BTIOfflineApplication(
      holder = createEORIDetails("holder_"),
      contact = Contact("", "", ""),
      agent = None,
      "",
      "",
      "",
      "",
      "",
      "",
      "")
  }

  def createLiabilityOrder: LiabilityOrder = {
    LiabilityOrder(
      holder = createEORIDetails("holder"),
      contact = Contact("", "", ""),
      LiabilityOrderType.LIVE,
      s"port_",
      s"entryNumber_",
      ZonedDateTime.now()
    )
  }

  def createEORIDetails(prefix: String): EORIDetails = {
    EORIDetails(s"eori_$prefix",
      s"trader-name_$prefix",
      s"addressLine1_$prefix", s"addressLine2_$prefix", s"addressLine3_$prefix",
      s"postcode_$prefix", s"country_$prefix")
  }

  def createCase(a: Application = createBTIApplication): Case = {
    Case(
      reference = RandomGenerator.randomUUID(),
      CaseStatus.NEW,
      assigneeId = Some(RandomGenerator.randomUUID()),
      application = a
    )
  }

}
