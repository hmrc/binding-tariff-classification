
package uk.gov.hmrc.component.data

import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.utils.RandomGenerator

object EventData {

  def createNoteEvent(caseReference: String): Event = {
    Event(
      id = RandomGenerator.randomUUID(),
      details = Note(Some("This is a note")),
      userId = RandomGenerator.randomUUID(),
      caseReference
    )
  }

  def createCaseStatusChangeEvent(caseReference: String): Event = {
    Event(
      id = RandomGenerator.randomUUID(),
      details = CaseStatusChange(from = CaseStatus.DRAFT, to = CaseStatus.NEW),
      userId = RandomGenerator.randomUUID(),
      caseReference
    )
  }
}