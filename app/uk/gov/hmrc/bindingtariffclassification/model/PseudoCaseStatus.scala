package uk.gov.hmrc.bindingtariffclassification.model

object PseudoCaseStatus extends Enumeration {
  type PseudoCaseStatus = Value
  val DRAFT, NEW, OPEN, SUPPRESSED, REFERRED, REJECTED, CANCELLED, SUSPENDED, COMPLETED, REVOKED, ANNULLED, LIVE, EXPIRED = Value
}
