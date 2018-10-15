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
      holder = createEORIDetails,
      contact = Contact("Marisa", "marisa@me.com", "0123456789"),
      goodDescription = "this is a BTI application for mobile phones",
      goodName = "mobile phones"
    )
  }

  def createLiabilityOrder: LiabilityOrder = {
    LiabilityOrder(
      holder = createEORIDetails,
      contact = Contact("Alfred", "alfred@me.com", "0198765432"),
      LiabilityStatus.LIVE,
      "port-A",
      "23-SGD",
      ZonedDateTime.now()
    )
  }

  def createEORIDetails: EORIDetails = {
    EORIDetails(RandomGenerator.randomUUID(),
      "John Lewis",
      "23, Leyton St", "Leeds", "West Yorkshire",
      "LS4 99AA",
      "GB")
  }

  def createCase(a: Application = createBTIApplication): Case = {
    Case(
      reference = RandomGenerator.randomUUID(),
      status = CaseStatus.NEW,
      assigneeId = Some(RandomGenerator.randomUUID()),
      application = a,
      attachments = Seq()
    )
  }

}
