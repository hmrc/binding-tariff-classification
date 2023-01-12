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

package uk.gov.hmrc.bindingtariffclassification.repository

import org.bson.conversions.Bson
import play.api.libs.json.Json.JsValueWrapper
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters._
import uk.gov.hmrc.bindingtariffclassification.model.{ApplicationUpdate, BTIUpdate, CaseUpdate, LiabilityUpdate}

import javax.inject.{Inject, Singleton}

@Singleton
class UpdateMapper @Inject() () extends Mapper {
  def updateApplication(update: ApplicationUpdate): Seq[(String, JsValueWrapper)] =
    update match {
      case BTIUpdate(applicationPdf) =>
        applicationPdf.map(att => field("application.applicationPdf", att)).getOrElse(Seq.empty)
      case LiabilityUpdate(traderName) =>
        traderName.map(name => field("application.traderName", name)).getOrElse(Seq.empty)
    }

  def updateCase(update: CaseUpdate): Bson = {
    val applicationFields = update.application
      .map(updateApplication)
      .getOrElse(Seq.empty)

    updateFields(applicationFields: _*)
  }
}
