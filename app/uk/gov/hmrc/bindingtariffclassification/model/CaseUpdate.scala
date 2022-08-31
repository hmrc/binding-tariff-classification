/*
 * Copyright 2022 HM Revenue & Customs
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

sealed abstract class UpdateType(val name: String)
object UpdateType {
  case object SetValue extends UpdateType("SET_VALUE")
  case object NoChange extends UpdateType("NO_CHANGE")
}

sealed abstract class Update[+A] {
  def map[B](f: A => B): Update[B] = this match {
    case SetValue(a) => SetValue(f(a))
    case NoChange    => NoChange
  }
  def getOrElse[AA >: A](default: => AA): AA = this match {
    case SetValue(a) => a
    case NoChange    => default
  }
}
case class SetValue[A](a: A) extends Update[A]
case object NoChange extends Update[Nothing]

sealed abstract class ApplicationUpdate {
  def `type`: ApplicationType.Value
}

case class BTIUpdate(
  // holder: Option[EORIDetails]                     = None,
  // contact: Option[Contact]                        = None,
  // agent: Option[Option[AgentDetails]]             = None,
  // offline: Option[Boolean]                        = None,
  // goodName: Option[String]                        = None,
  // goodDescription: Option[String]                 = None,
  // confidentialInformation: Option[Option[String]] = None,
  // otherInformation: Option[Option[String]]        = None,
  // reissuedBTIReference: Option[Option[String]]    = None,
  // relatedBTIReferences: Option[List[String]]      = None,
  // knownLegalProceedings: Option[Option[String]]   = None,
  // envisagedCommodityCode: Option[Option[String]]  = None,
  // sampleToBeProvided: Option[Boolean]             = None,
  // sampleIsHazardous: Option[Option[Boolean]]      = None,
  // sampleToBeReturned: Option[Boolean]             = None,
  applicationPdf: Update[Option[Attachment]] = NoChange
) extends ApplicationUpdate {
  val `type`: ApplicationType.Value = ApplicationType.BTI
}

case class LiabilityUpdate(
  // contact: Option[Contact]                                   = None,
  // goodName: Option[Option[String]]                           = None,
  // status: Option[LiabilityStatus.Value]                      = None,
  traderName: Update[String] = NoChange
  // entryDate: Option[Option[Instant]]                         = None,
  // entryNumber: Option[Option[String]]                        = None,
  // traderCommodityCode: Option[Option[String]]                = None,
  // officerCommodityCode: Option[Option[String]]               = None,
  // btiReference: Option[Option[String]]                       = None,
  // repaymentClaim: Option[Option[RepaymentClaim]]             = None,
  // dateOfReceipt: Option[Option[Instant]]                     = None,
  // traderContactDetails: Option[Option[TraderContactDetails]] = None,
  // agentName: Option[Option[String]]                          = None,
  // port: Option[Option[String]]                               = None
) extends ApplicationUpdate {
  val `type`: ApplicationType.Value = ApplicationType.LIABILITY_ORDER
}

// case class CorrespondenceUpdate(
//   ) extends ApplicationUpdate {
//   val `type`: ApplicationType.Value = ApplicationType.CORRESPONDENCE
// }

// case class MiscUpdate(
//   ) extends ApplicationUpdate {
//   val `type`: ApplicationType.Value = ApplicationType.MISCELLANEOUS
// }

case class CaseUpdate(
  // status: Option[CaseStatus.Value]             = None,
  // createdDate: Option[Instant]                 = None,
  // caseBoardsFileNumber: Option[Option[String]] = None,
  // assignee: Option[Option[Operator]]           = None,
  // queueId: Option[Option[String]]              = None,
  application: Option[ApplicationUpdate] = None
  // decision: Option[Option[DecisionUpdate]]     = None,
  // attachments: Option[Seq[Attachment]]         = None,
  // keywords: Option[Set[String]]                = None,
  // sample: Option[Sample]                       = None,
  // dateOfExtract: Option[Option[Instant]]       = None,
  // migratedDaysElapsed: Option[Option[Long]]    = None
)
