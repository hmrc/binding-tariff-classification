
package it.uk.gov.hmrc.component.data

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