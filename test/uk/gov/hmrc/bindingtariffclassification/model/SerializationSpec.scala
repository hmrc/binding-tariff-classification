/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.libs.json.Json
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.{formatAgentDetails, formatAppeal, formatAppealAdded, formatAssignmentChange, formatAttachment, formatBTIApplication, formatCancellation, formatCancellationCaseStatusChange, formatCaseHeader, formatCaseKeyword, formatCaseStatusChange, formatCompletedCaseStatusChange, formatContact, formatCorrespondence, formatEORIDetails, formatEvent, formatExtendedUseStatusChange, formatLiabilityOrder, formatManageKeywordsData, formatMisc, formatNote, formatOperator, formatQueueChange, formatReferralCaseStatusChange, formatRejectCaseStatusChange, formatSample, formatSampleReturnChange, formatSampleSendChange, formatSampleStatusChange, messageLoggedFormat}
import uk.gov.hmrc.bindingtariffclassification.model.bta.BtaApplications.format
import uk.gov.hmrc.bindingtariffclassification.model.bta.{BtaApplications, BtaCard, BtaRulings}
import uk.gov.hmrc.bindingtariffclassification.model.bta.BtaCard.format

import java.time.Instant

class SerializationSpec extends BaseSpec {

  private val eoriDetails = EORIDetails(
    eori = "eori_12345",
    businessName = "hmrc",
    addressLine1 = "thames",
    addressLine2 = "num 34",
    addressLine3 = "floor 5",
    postcode = "ttt333",
    country = "UK"
  )

  private val operator = Operator(
    id = "operatorId",
    name = Option("Nissan"),
    email = Option("test@test.com"),
    role = Role.CLASSIFICATION_MANAGER,
    memberOfTeams = List("custodians"),
    managerOfTeams = List("liveServices"),
    deleted = false
  )

  private val attachment = Attachment(
    id = "attId",
    public = false,
    operator = Option(operator),
    timestamp = Instant.EPOCH,
    description = Option("desc"),
    shouldPublishToRulings = false
  )

  private val contact = Contact("Charles", "test@test.com", None)

  private val address = Address(
    buildingAndStreet = "Evergreen 305",
    townOrCity = "Springfield",
    county = Option("TX"),
    postCode = Option("E123123")
  )

  private val message = Message("Charles", Instant.EPOCH, "running out of fuel")

  private val agentDetails = AgentDetails(eoriDetails = eoriDetails, letterOfAuthorisation = Option(attachment))

  private val caseH = CaseHeader(
    reference = "ref1",
    assignee = Option(operator),
    team = Option("custodians"),
    goodsName = Option("shorts"),
    caseType = ApplicationType.BTI,
    status = CaseStatus.REFERRED,
    daysElapsed = 5L,
    liabilityStatus = Option(LiabilityStatus.LIVE)
  )

  private val caseK = CaseKeyword(keyword = Keyword(name = "Tom", approved = true), cases = List(caseH))

  "Details" should {

    "Serialize and Deserialize JSON for CompletedCaseStatusChange" in {

      val details = CompletedCaseStatusChange(CaseStatus.COMPLETED, Some("comment"), None)

      val json         = Json.toJson(details)
      val deserialized = json.as[CompletedCaseStatusChange]

      deserialized.shouldBe(details)
    }

    "Serialize and Deserialize JSON for ExtendedUseStatusChange" in {

      val details = ExtendedUseStatusChange(true, true, Option("comment"))

      val json         = Json.toJson(details)
      val deserialized = json.as[ExtendedUseStatusChange]

      deserialized.shouldBe(details)
    }

    "Serialize and Deserialize JSON for QueueChange" in {

      val details = QueueChange(Option("to"), Option("from"), Option("comment"))

      val json         = Json.toJson(details)
      val deserialized = json.as[QueueChange]

      deserialized.shouldBe(details)
    }

    "Serialize and Deserialize JSON for SampleSendChange" in {

      val details = SampleSendChange(from = Option(SampleSend.TRADER), to = Option(SampleSend.AGENT), Option("comment"))

      val json         = Json.toJson(details)
      val deserialized = json.as[SampleSendChange]

      deserialized.shouldBe(details)
    }

    "Serialize and Deserialize JSON for SampleReturnChange" in {

      val details = SampleReturnChange(from = Option(SampleReturn.NO), to = Option(SampleReturn.YES), Option("comment"))

      val json         = Json.toJson(details)
      val deserialized = json.as[SampleReturnChange]

      deserialized.shouldBe(details)
    }

    "Serialize and Deserialize JSON for SampleStatusChange" in {

      val details = SampleStatusChange(
        from = Option(SampleStatus.STORAGE),
        to = Option(SampleStatus.SENT_FOR_ANALYSIS),
        Option("comment")
      )

      val json         = Json.toJson(details)
      val deserialized = json.as[SampleStatusChange]

      deserialized.shouldBe(details)
    }

    "Serialize and Deserialize JSON for RejectCaseStatusChange" in {

      val details = RejectCaseStatusChange(
        from = CaseStatus.NEW,
        to = CaseStatus.REJECTED,
        reason = RejectReason.NO_INFO_FROM_TRADER
      )

      val json         = Json.toJson(details)
      val deserialized = json.as[RejectCaseStatusChange]

      deserialized.shouldBe(details)
    }

    "Serialize and Deserialize JSON for an Event" in {
      val details = QueueChange(Option("to"), Option("from"), Option("comment"))

      val event = Event(
        details = details,
        operator = Operator("operatorId"),
        caseReference = "ref",
        timestamp = Instant.EPOCH
      )

      val json         = Json.toJson(event)
      val deserialized = json.as[Event]

      deserialized.shouldBe(event)
      event.details.shouldBe(QueueChange(Option("to"), Option("from"), Option("comment")))
    }

    "Serialize and Deserialize JSON for Operator" in {
      val json         = Json.toJson(operator)
      val deserialized = json.as[Operator]

      deserialized.shouldBe(operator)
    }

    "Serialize and Deserialize JSON for an Attachment" in {
      val json         = Json.toJson(attachment)
      val deserialized = json.as[Attachment]

      deserialized.shouldBe(attachment)
    }

    "Serialize and Deserialize JSON for AssignmentChange" in {
      val details = AssignmentChange(
        from = Some(operator),
        to = Some(operator.copy(id = "smoothOperator")),
        Option("comment")
      )

      val json         = Json.toJson(details)
      val deserialized = json.as[AssignmentChange]

      deserialized.shouldBe(details)
    }

    "Serialize and Deserialize JSON for EORIDetails" in {
      val json         = Json.toJson(eoriDetails)
      val deserialized = json.as[EORIDetails]

      deserialized.shouldBe(eoriDetails)
    }

    "Serialize and Deserialize JSON for AgentDetails" in {
      val json         = Json.toJson(agentDetails)
      val deserialized = json.as[AgentDetails]

      deserialized.shouldBe(agentDetails)
    }

    "Serialize and Deserialize JSON for Contact" in {
      val json         = Json.toJson(contact)
      val deserialized = json.as[Contact]

      deserialized.shouldBe(contact)
    }

    "Serialize and Deserialize JSON for Message" in {
      val json         = Json.toJson(message)
      val deserialized = json.as[Message]

      deserialized.shouldBe(message)
    }

    "Serialize and Deserialize JSON for LiabilityOrder" in {
      val order = LiabilityOrder(
        contact = contact,
        goodName = Some("season tickets"),
        status = LiabilityStatus.LIVE,
        traderName = "Josh",
        entryDate = Option(Instant.EPOCH),
        entryNumber = Option("124"),
        traderCommodityCode = Option("1244"),
        officerCommodityCode = Option("1244"),
        btiReference = Option("ref_1244"),
        repaymentClaim = Option(RepaymentClaim(dvrNumber = Option("356"), dateForRepayment = Option(Instant.EPOCH))),
        dateOfReceipt = Option(Instant.EPOCH),
        traderContactDetails = Option(
          TraderContactDetails(email = Option("test@test.com"), phone = Option("0777777"), address = Option(address))
        ),
        agentName = Option("Charles"),
        port = Option("San Andreas")
      )
      val json         = Json.toJson(order)
      val deserialized = json.as[LiabilityOrder]

      deserialized.shouldBe(order)
    }

    "Serialize and Deserialize JSON for BTIApplication" in {
      val app = BTIApplication(
        holder = eoriDetails,
        contact = contact,
        agent = Option(agentDetails),
        offline = false,
        goodName = "break discs",
        goodDescription = "used to break",
        confidentialInformation = Option("n.a"),
        otherInformation = Option("n.a"),
        reissuedBTIReference = Option("n.a"),
        relatedBTIReference = Option("n.a"),
        relatedBTIReferences = List("n.a"),
        knownLegalProceedings = Option("n.a"),
        envisagedCommodityCode = Option("n.a"),
        sampleToBeProvided = false,
        sampleIsHazardous = Option(false),
        sampleToBeReturned = true,
        applicationPdf = Option(attachment)
      )
      val json         = Json.toJson(app)
      val deserialized = json.as[BTIApplication]

      deserialized.shouldBe(app)
    }

    "Serialize and Deserialize JSON for CorrespondenceApplication" in {
      val app = CorrespondenceApplication(
        correspondenceStarter = Option("starter"),
        agentName = Option("Sean"),
        address = address,
        contact = contact,
        fax = Option("fax"),
        summary = "no comments",
        detailedDescription = "same",
        relatedBTIReference = Option("n.a"),
        relatedBTIReferences = List("n.a"),
        sampleToBeProvided = true,
        sampleToBeReturned = false,
        messagesLogged = List(message)
      )
      val json         = Json.toJson(app)
      val deserialized = json.as[CorrespondenceApplication]

      deserialized.shouldBe(app)
    }

    "Serialize and Deserialize JSON for Cancellation" in {
      val cancellation = Cancellation(
        reason = CancelReason.INVALIDATED_CODE_CHANGE,
        applicationForExtendedUse = true
      )

      val json         = Json.toJson(cancellation)
      val deserialized = json.as[Cancellation]

      deserialized.shouldBe(cancellation)
    }

    "Serialize and Deserialize JSON for CaseStatusChange" in {
      val change = CaseStatusChange(
        from = CaseStatus.NEW,
        to = CaseStatus.REJECTED,
        comment = Option("comment"),
        attachmentId = Option("attachment1")
      )

      val json         = Json.toJson(change)
      val deserialized = json.as[CaseStatusChange]

      deserialized.shouldBe(change)
    }

    "Serialize and Deserialize JSON for MiscApplication" in {
      val misc = MiscApplication(
        contact = contact,
        name = "misc1",
        contactName = Option(contact.name),
        caseType = MiscCaseType.IB,
        detailedDescription = Option("des"),
        sampleToBeProvided = true,
        sampleToBeReturned = false,
        messagesLogged = List(message)
      )

      val json         = Json.toJson(misc)
      val deserialized = json.as[MiscApplication]

      deserialized.shouldBe(misc)
    }

    "Serialize and Deserialize JSON for Appeal" in {
      val appeal = Appeal(
        id = "id1",
        status = AppealStatus.MEDIATION,
        `type` = AppealType.APPEAL_TIER_2
      )

      val json         = Json.toJson(appeal)
      val deserialized = json.as[Appeal]

      deserialized.shouldBe(appeal)
    }

    "Serialize and Deserialize JSON for Sample" in {
      val sample = Sample(
        status = Option(SampleStatus.STORAGE),
        requestedBy = Option(operator),
        returnStatus = Option(SampleReturn.YES),
        whoIsSending = Option(SampleSend.TRADER)
      )

      val json         = Json.toJson(sample)
      val deserialized = json.as[Sample]

      deserialized.shouldBe(sample)
    }
    "Serialize and Deserialize JSON for CaseHeader" in {
      val json         = Json.toJson(caseH)
      val deserialized = json.as[CaseHeader]

      deserialized.shouldBe(caseH)
    }

    "Serialize and Deserialize JSON for CaseKeyword" in {
      val json         = Json.toJson(caseK)
      val deserialized = json.as[CaseKeyword]

      deserialized.shouldBe(caseK)
    }

    "Serialize and Deserialize JSON for ManageKeywordsData" in {
      val kData = ManageKeywordsData(
        pagedCaseKeywords = Paged(Seq(caseK)),
        pagedKeywords = Paged(Seq(Keyword(name = "Tom", approved = true)))
      )
      val json         = Json.toJson(kData)
      val deserialized = json.as[ManageKeywordsData]

      deserialized.shouldBe(kData)
    }

    "Serialize and Deserialize JSON for CancellationCaseStatusChange" in {
      val statusChange = CancellationCaseStatusChange(
        from = CaseStatus.REJECTED,
        comment = Option("comment"),
        attachmentId = Option("attch1"),
        reason = CancelReason.ANNULLED
      )
      val json         = Json.toJson(statusChange)
      val deserialized = json.as[CancellationCaseStatusChange]

      deserialized.shouldBe(statusChange)
    }

    "Serialize and Deserialize JSON for ReferralCaseStatusChange" in {
      val referral = ReferralCaseStatusChange(
        from = CaseStatus.REFERRED,
        comment = Option("comment"),
        attachmentId = Option("attach1"),
        referredTo = "Ringo",
        reason = Seq(ReferralReason.REQUEST_MORE_INFO)
      )
      val json         = Json.toJson(referral)
      val deserialized = json.as[ReferralCaseStatusChange]

      deserialized.shouldBe(referral)
    }

    "Serialize and Deserialize JSON for AppealAdded" in {
      val appeal = AppealAdded(
        appealType = AppealType.APPEAL_TIER_2,
        appealStatus = AppealStatus.MEDIATION,
        comment = Option("no comment")
      )
      val json         = Json.toJson(appeal)
      val deserialized = json.as[AppealAdded]

      deserialized.shouldBe(appeal)
    }

    "Serialize and Deserialize JSON for BtaApplications" in {
      val apps = BtaApplications(
        total = 4,
        actionable = 3
      )
      val json         = Json.toJson(apps)
      val deserialized = json.as[BtaApplications]

      deserialized.shouldBe(apps)
    }

    "Serialize and Deserialize JSON for BtaCard" in {
      val apps    = BtaApplications(total = 4, actionable = 3)
      val rulings = BtaRulings(total = 3, expiring = 2)
      val bta     = BtaCard(eori = "123GB", applications = Option(apps), rulings = Option(rulings))

      val json         = Json.toJson(bta)
      val deserialized = json.as[BtaCard]

      deserialized.shouldBe(bta)
    }
  }
}
