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

package uk.gov.hmrc.bindingtariffclassification.model.bta

import play.api.libs.json.*
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import uk.gov.hmrc.bindingtariffclassification.model.bta.BtaApplications.format
import uk.gov.hmrc.bindingtariffclassification.model.bta.BtaRulings.format

case class BtaCard(eori: String, applications: Option[BtaApplications], rulings: Option[BtaRulings])

object BtaCard {
  implicit val format: Format[BtaCard] = (
    (JsPath \ "eori").format[String] and
      (JsPath \ "applications").formatNullable[BtaApplications] and
      (JsPath \ "rulings").formatNullable[BtaRulings]
  )(BtaCard.apply, o => Tuple.fromProductTyped(o))
}
