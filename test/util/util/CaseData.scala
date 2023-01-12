/*
 * Copyright 2023 HM Revenue & Customs
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

import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.Role.Role
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.utils.RandomGenerator

import java.time.{Instant, LocalDate, ZoneId}

object CaseData {

  private val secondsInAYear = 3600 * 24 * 365

  private def createContact: Contact =
    Contact("Maurizio", "maurizio@me.com", Some("0123456789"))

  def createBasicBTIApplication: BTIApplication =
    BTIApplication(
      holder          = createEORIDetails,
      contact         = createContact,
      goodDescription = "this is a BTI application for HTC Wildfire mobile phones",
      goodName        = "HTC Wildfire smartphone"
    )

  def createBTIApplicationWithAllFields(
    applicationPdf: Option[Attachment]        = Some(createAttachment),
    letterOfAuthorization: Option[Attachment] = Some(createAttachment)
  ): BTIApplication =
    BTIApplication(
      holder                  = createEORIDetails,
      agent                   = Some(createAgentDetails(letterOfAuthorization = letterOfAuthorization)),
      contact                 = createContact,
      goodDescription         = "this is a BTI application for HTC Wildfire mobile phones",
      goodName                = "HTC Wildfire smartphone",
      confidentialInformation = Some("This phone has a secret processor."),
      otherInformation        = Some("The phone comes in multiple colors"),
      reissuedBTIReference    = Some("BTI123"),
      relatedBTIReference     = Some("BTI987"),
      knownLegalProceedings   = Some("Someone is suing me!"),
      envisagedCommodityCode  = Some("12345"),
      sampleToBeProvided      = true,
      sampleToBeReturned      = true,
      applicationPdf          = applicationPdf
    )

  def createDecision(
    bindingCommodityCode: String                 = "12345678",
    effectiveStartDate: Option[Instant]          = Some(Instant.now()),
    effectiveEndDate: Option[Instant]            = Some(Instant.now().plusSeconds(3 * secondsInAYear)),
    methodSearch: Option[String]                 = Some("bike spanner"),
    justification: String                        = "Found precedent case",
    goodsDescription: String                     = "Bike tool",
    methodCommercialDenomination: Option[String] = None,
    decisionPdf: Option[Attachment]              = Some(createAttachment)
  ): Decision =
    Decision(
      bindingCommodityCode         = bindingCommodityCode,
      effectiveStartDate           = effectiveStartDate,
      effectiveEndDate             = effectiveEndDate,
      methodSearch                 = methodSearch,
      justification                = justification,
      goodsDescription             = goodsDescription,
      methodCommercialDenomination = methodCommercialDenomination,
      decisionPdf                  = decisionPdf
    )

  def createLiabilityOrder: LiabilityOrder =
    LiabilityOrder(
      contact              = createContact,
      status               = LiabilityStatus.LIVE,
      traderName           = "John Lewis",
      goodName             = Some("Hair dryer"),
      entryNumber          = Some("23-SGD"),
      entryDate            = Some(Instant.now()),
      traderCommodityCode  = Some("1234567890"),
      officerCommodityCode = Some("0987654321")
    )

  def createLiabilityOrderWithExtraFields: LiabilityOrder =
    LiabilityOrder(
      contact              = createContact,
      status               = LiabilityStatus.LIVE,
      traderName           = "Acme Corp.",
      goodName             = Some("Large Iron Anvil"),
      entryNumber          = Some("23-SGD"),
      entryDate            = Some(Instant.now()),
      traderCommodityCode  = Some("6666666666"),
      officerCommodityCode = Some("0987654321"),
      btiReference         = Some("BTI-REFERENCE"),
      repaymentClaim       = Some(RepaymentClaim(dvrNumber = Some("DVR-123456"), dateForRepayment = Some(Instant.now()))),
      dateOfReceipt        = Some(Instant.now()),
      traderContactDetails = Some(
        TraderContactDetails(
          Some("email"),
          Some("phone"),
          Some(Address("Street Name", "Town", Some("County"), Some("P0ST C05E")))
        )
      ),
      agentName = Some("agent"),
      port      = Some("port")
    )

  def createCorrespondenceApplication: CorrespondenceApplication =
    CorrespondenceApplication(
      None,
      Some("agent"),
      createAddress,
      createContact,
      None,
      "Laptop",
      "Personal Computer",
      sampleToBeProvided = false,
      sampleToBeReturned = false
    )

  def createMiscApplication: MiscApplication =
    MiscApplication(
      createContact,
      "name",
      Some("contactName"),
      MiscCaseType.HARMONISED,
      None,
      sampleToBeProvided = false,
      sampleToBeReturned = false
    )

  def createAgentDetails(
    letterOfAuthorization: Option[Attachment] = Some(createAttachment.copy(public = false))
  ): AgentDetails =
    AgentDetails(
      eoriDetails           = createEORIDetails.copy(businessName = "Frank Agent-Smith"),
      letterOfAuthorisation = letterOfAuthorization
    )

  def createEORIDetails: EORIDetails =
    EORIDetails(
      RandomGenerator.randomUUID(),
      "John Lewis",
      "23, Leyton St",
      "Leeds",
      "West Yorkshire",
      "LS4 99AA",
      "GB"
    )

  def createAddress: Address =
    Address(
      "23, Leyton St",
      "Leeds",
      Some("West Yorkshire"),
      Some("LS4 99AA")
    )

  def eORIDetailForNintedo: EORIDetails =
    EORIDetails(
      RandomGenerator.randomUUID(),
      "Nintendo",
      "111, Brodway St",
      "Leeds",
      "West Yorkshire",
      "LS11 22BB",
      "GB"
    )

  def createNewCase(
    app: Application             = createBasicBTIApplication,
    attachments: Seq[Attachment] = Seq.empty
  ): NewCaseRequest =
    NewCaseRequest(
      application = app,
      attachments = attachments
    )

  def createNewUser(user: Operator = createUser()): NewUserRequest =
    NewUserRequest(operator = user)

  def createNewKeyword(keyword: Keyword = createKeyword()): NewKeywordRequest =
    NewKeywordRequest(keyword = keyword)

  def createNewCaseWithExtraFields(): Case =
    Case(
      reference   = "9999999999",
      status      = CaseStatus.OPEN,
      createdDate = Instant.now.minusSeconds(1 * secondsInAYear),
      queueId     = Some("3"),
      assignee    = Some(Operator("0")),
      application = createBasicBTIApplication,
      decision    = Some(createDecision()),
      attachments = Seq.empty,
      keywords    = Set("bike", "tool")
    )

  private def decision = Decision(
    bindingCommodityCode = "code",
    justification        = "something",
    goodsDescription     = "desc"
  )

  def createBtaCardData(
    eori: String,
    totalApplications: Int,
    actionableApplications: Int,
    totalRulings: Int,
    expiringRulings: Int,
    expiryMonths: Option[Int] = None,
    expiryDays: Option[Int]   = None
  ): List[Case] =
    if (actionableApplications > totalApplications || expiringRulings > totalRulings) {
      List.empty
    } else {
      def dateToInstant(localDate: LocalDate) = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant
      val now                                 = LocalDate.now()
      val actionableA                         = (1 to actionableApplications).map(_ => createCase(status = CaseStatus.REFERRED))
      val totalA                              = (1 to (totalApplications - actionableApplications)).map(_ => createCase(status = CaseStatus.OPEN))
      val expiringR = (1 to expiringRulings).map { _ =>
        createCase(
          status = CaseStatus.COMPLETED,
          decision = Some(
            decision.copy(
              effectiveStartDate = Some(dateToInstant(now)),
              effectiveEndDate = Some(
                dateToInstant(now.plusMonths(expiryMonths.getOrElse(1).toLong).plusDays(expiryDays.getOrElse(0).toLong))
              )
            )
          )
        )
      }
      val totalR = (1 to (totalRulings - expiringRulings)).map(_ =>
        createCase(
          status = CaseStatus.COMPLETED,
          decision = Some(
            decision.copy(
              effectiveStartDate = Some(dateToInstant(now)),
              effectiveEndDate   = Some(dateToInstant(now.plusYears(1)))
            )
          )
        )
      )
      List(actionableA, totalA, expiringR, totalR).flatten.map(i =>
        i.copy(application =
          i.application.asBTI
            .copy(holder = EORIDetails(eori, "name", "l1", "l2", "l3", "postcode", "uk"))
        )
      )
    }

  def createCase(
    app: Application               = createBasicBTIApplication,
    r: String                      = RandomGenerator.randomUUID(),
    status: CaseStatus             = CaseStatus.NEW,
    decision: Option[Decision]     = None,
    queue: Option[String]          = None,
    assignee: Option[Operator]     = None,
    attachments: Seq[Attachment]   = Seq.empty,
    keywords: Set[String]          = Set.empty,
    dateOfExtract: Option[Instant] = None
  ): Case =
    Case(
      reference     = r,
      status        = status,
      queueId       = queue,
      assignee      = assignee,
      application   = app,
      decision      = decision,
      attachments   = attachments,
      keywords      = keywords,
      dateOfExtract = dateOfExtract
    )

  def createAttachment: Attachment =
    Attachment(
      id                     = RandomGenerator.randomUUID(),
      public                 = true,
      shouldPublishToRulings = true
    )

  def createAttachmentWithOperator: Attachment =
    Attachment(
      id                     = RandomGenerator.randomUUID(),
      public                 = true,
      operator               = Some(Operator(id = "0", Some("OperatorName"))),
      shouldPublishToRulings = false
    )

  def createUser(
    id: String                   = "user-id",
    name: Option[String]         = None,
    email: Option[String]        = None,
    role: Role                   = Role.CLASSIFICATION_OFFICER,
    memberOfTeams: List[String]  = List.empty,
    managerOfTeams: List[String] = List.empty,
    deleted: Boolean             = false
  ): Operator =
    Operator(id, name, email, role, memberOfTeams, managerOfTeams, deleted)

  def createKeyword(name: String = "keyword example", approved: Boolean = false): Keyword =
    Keyword(name = name, approved = approved)
}
