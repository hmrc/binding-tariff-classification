/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.Singleton
import play.api.libs.json._
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.search.{Filter, Search, Sort}

@Singleton
class SearchMapper {

  private def nullifyNoneValues: String => JsValue = { v: String =>
    v match {
      case "none" => JsNull
      case _ => JsString(v)
    }
  }

  private def notEqualFilter(field: String): JsObject = {
    Json.obj("$ne" -> field)
  }

  def filterBy(filter: Filter): JsObject = {
    JsObject(
      Map() ++
        filter.queueId.map("queueId" -> nullifyNoneValues(_)) ++
        filter.assigneeId.map("assignee.id" -> nullifyNoneValues(_)) ++
        filter.status.map(splitByComma).map(toSearchArray).map("status" -> _) ++
        filter.reference.map("reference" -> nullifyNoneValues(_)) ++
        filter.traderName.map("application.holder.businessName" -> nullifyNoneValues(_))
    )
  }

  def sortBy(sort: Sort): JsObject = {
    sort.field match {
      case Some(s) => Json.obj(s -> sort.direction.get.id)
      case _ => Json.obj()
    }
  }

  private def toSearchArray: Seq[String] => JsObject = {
    values => JsObject(Map("$in" -> JsArray(values.map(JsString))))
  }

  private def splitByComma(string: String): Seq[String] = {
    string.split(",").toSeq
  }

  def reference(reference: String): JsObject = {
    Json.obj("reference" -> reference)
  }

  def fromReferenceAndStatus(reference: String, notAllowedStatus: CaseStatus): JsObject = {
    Json.obj("reference" -> reference, "status" -> notEqualFilter(notAllowedStatus.toString))
  }

  def updateField(fieldName: String, fieldValue: String): JsObject = {
    Json.obj("$set" -> Json.obj(fieldName -> fieldValue))
  }

}
