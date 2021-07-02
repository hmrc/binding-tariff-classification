/*
 * Copyright 2021 HM Revenue & Customs
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

import org.mongodb.scala.bson.BsonDocument

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.bindingtariffclassification.model.{ApplicationUpdate, CaseUpdate}
import play.api.libs.json._
import play.api.libs.json.Json.JsValueWrapper
import uk.gov.hmrc.bindingtariffclassification.model.{BTIUpdate, LiabilityUpdate}
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters._

@Singleton
class UpdateMapper @Inject() () extends Mapper {
  def updateApplication(update: ApplicationUpdate) = {
    update match {
      case BTIUpdate(applicationPdf) => applicationPdf.map(_.fold(Seq.empty[BsonDocument]) { attachment =>
        Seq(BsonDocument("application.applicationPdf" -> Json.toJson(attachment).toString()))}).getOrElse(Seq.empty)
      case LiabilityUpdate(traderName) => traderName.map { attachment =>
        Seq(BsonDocument("application.traderName" -> Json.toJson(attachment).toString()))}.getOrElse(Seq.empty)
    }
  }

  def updateCase(update: CaseUpdate): BsonDocument = {
    val applicationFields = update.application
      .map(updateApplication)
      .getOrElse(Seq.empty)
    updateFields(applicationFields)
  }
}
