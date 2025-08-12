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
  applicationPdf: Update[Option[Attachment]] = NoChange
) extends ApplicationUpdate {
  val `type`: ApplicationType.Value = ApplicationType.BTI
}

case class LiabilityUpdate(
  traderName: Update[String] = NoChange
) extends ApplicationUpdate {
  val `type`: ApplicationType.Value = ApplicationType.LIABILITY_ORDER
}

case class CaseUpdate(application: Option[ApplicationUpdate] = None)
