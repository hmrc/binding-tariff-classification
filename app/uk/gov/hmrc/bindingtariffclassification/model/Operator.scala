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

package uk.gov.hmrc.bindingtariffclassification.model

import uk.gov.hmrc.bindingtariffclassification.model.Role.Role

case class Operator(
  id: String,
  name: Option[String]         = None,
  email: Option[String]        = None,
  role: Role                   = Role.CLASSIFICATION_OFFICER,
  memberOfTeams: List[String]  = List.empty,
  managerOfTeams: List[String] = List.empty,
  deleted: Boolean             = false
) {

  def manager: Boolean = role == Role.CLASSIFICATION_MANAGER
}

object Role extends Enumeration {
  type Role = Value
  val CLASSIFICATION_OFFICER, CLASSIFICATION_MANAGER, READ_ONLY = Value
}
