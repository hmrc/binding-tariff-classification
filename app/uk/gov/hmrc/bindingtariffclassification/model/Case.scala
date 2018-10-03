package uk.gov.hmrc.bindingtariffclassification.model

import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.CaseType.CaseType

case class Case
(
  reference: String,
  `type`: CaseType,
  status: CaseStatus,
  assigneeId: String,
  queueId: String,
  //financialAccountantsAddress: String, // check with Matt
  //transactionType: String, // check with Matt
  //customsNomenclature: String, // check with Matt
  application: Application,
  decision: Decision
)

object CaseType extends Enumeration {
  type CaseType = Value
  val BTI, LIABILITY_ORDER, OFFLINE_BTI = Value
}

object CaseStatus extends Enumeration {
  type CaseStatus = Value
  val DRAFT, NEW, OPEN, SUPPRESSED, REFERRED, REJECTED, CANCELLED, SUSPENDED, DECISION_MADE, REVOKED, ANNULLED = Value
}