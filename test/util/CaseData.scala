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

package util

import java.time.ZonedDateTime

import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.utils.RandomGenerator

object CaseData {

  private def createContact: Contact = {
    Contact("Maurizio", "maurizio@me.com", Some("0123456789"))
  }

  def createBasicBTIApplication: BTIApplication = {
    BTIApplication(
      holder = createEORIDetails,
      contact = createContact,
      goodDescription = "this is a BTI application for HTC Wildfire mobile phones",
      goodName = "HTC Wildfire smartphone"
    )
  }

  def createBTIApplicationWithAllFields: BTIApplication = {
    BTIApplication(
      holder = createEORIDetails,
      agent = Some(createAgentDetails),
      contact = createContact,
      goodDescription = "this is a BTI application for HTC Wildfire mobile phones",
      goodName = "HTC Wildfire smartphone",
      confidentialInformation = Some("This phone has a secret processor."),
      otherInformation = Some("The phone comes in multiple colors"),
      reissuedBTIReference = Some("BTI123"),
      relatedBTIReference = Some("BTI987"),
      knownLegalProceedings = Some("Someone is suing me!"),
      envisagedCommodityCode = Some("AS12345LG"),
      sampleToBeProvided = true,
      sampleToBeReturned = true
    )
  }

  def createDecision: Decision = {
    Decision(
      bindingCommodityCode = "GB1234567",
      effectiveStartDate = ZonedDateTime.now(),
      effectiveEndDate = ZonedDateTime.now().plusYears(3),
      methodSearch = Some("bike spanner"),
      justification = "Found precedent case",
      goodsDescription = "Bike tool",
      keywords = Seq("bike", "tool")
    )
  }

  def createLiabilityOrder: LiabilityOrder = {
    LiabilityOrder(
      holder = createEORIDetails,
      contact = createContact,
      LiabilityStatus.LIVE,
      "port-A",
      "23-SGD",
      ZonedDateTime.now()
    )
  }

  private def createAgentDetails: AgentDetails = {
    AgentDetails(
      eoriDetails = createEORIDetails.copy(traderName = "Frank Agent-Smith"),
      letterOfAuthorisation = createAttachment.copy(application = true, public = false)
    )
  }

  def createEORIDetails: EORIDetails = {
    EORIDetails(RandomGenerator.randomUUID(),
      "John Lewis",
      "23, Leyton St", "Leeds", "West Yorkshire",
      "LS4 99AA",
      "GB")
  }

  def createNewCase(app: Application = createBasicBTIApplication,
                    attachments: Seq[Attachment] = Seq.empty): NewCaseRequest = {
    NewCaseRequest(
      application = app,
      attachments = attachments
    )
  }

  def createNewCaseWithExtraFields(): Case = {
    Case(
      reference = "9999999999",
      status = CaseStatus.OPEN,
      createdDate = ZonedDateTime.now.minusYears(1),
      queueId = Some("3"),
      assigneeId = Some("0"),
      application = createBasicBTIApplication,
      decision = Some(createDecision),
      closedDate = Some(ZonedDateTime.now().minusYears(1)),
      attachments = Seq.empty
    )
  }

  def createCase(app: Application = createBasicBTIApplication,
                 r: String = RandomGenerator.randomUUID(),
                 decision: Option[Decision] = None,
                 queue: Option[String] = None,
                 assignee: Option[String] = None,
                 attachments: Seq[Attachment] = Seq.empty): Case = {
    Case(
      reference = r,
      status = CaseStatus.NEW,
      queueId = queue,
      assigneeId = assignee,
      application = app,
      decision = decision,
      attachments = attachments
    )
  }

  def createAttachment: Attachment = {
    Attachment(
      application = false,
      public = true,
      url = "files://aws.bucket/12345",
      mimeType = "media/jpg"
    )
  }

}
