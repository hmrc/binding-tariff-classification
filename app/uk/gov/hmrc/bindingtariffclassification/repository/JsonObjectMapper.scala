/*
 * Copyright 2018 HM Revenue & Customs
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
import uk.gov.hmrc.bindingtariffclassification.model.search.CaseParamsFilter

@Singleton
class JsonObjectMapper {

  private def nullifyNoneValues: String => JsValue = { v: String =>
    v match {
      case "none" => JsNull
      case _ => JsString(v)
    }
  }

  private def notEqualFilter(forbiddenFieldValue: String): JsObject = {
    // TODO: extend this to a list of forbidden values
    Json.obj("$ne" -> forbiddenFieldValue)
  }

  private def toInFilter: Seq[String] => JsObject = {
    values => JsObject(Map("$in" -> JsArray(values.map(JsString))))
  }

  def from: CaseParamsFilter => JsObject = { searchCase =>
    val queueFilter = searchCase.queueId.map("queueId" -> nullifyNoneValues(_))
    val assigneeFilter = searchCase.assigneeId.map("assigneeId" -> nullifyNoneValues(_))
    val statusFilter = searchCase.status.map(toInFilter).map("status" -> _)

    JsObject(Map() ++ queueFilter ++ assigneeFilter ++ statusFilter)
  }

  def fromReference(reference: String): JsObject = {
    Json.obj("reference" -> reference)
  }

  def fromReferenceAndStatus(reference: String, notAllowedStatus: CaseStatus): JsObject = {
    Json.obj("reference" -> reference, "status" -> notEqualFilter(notAllowedStatus.toString))
  }

  def updateField(fieldName: String, fieldValue: String): JsObject = {
    Json.obj("$set" -> Json.obj(fieldName -> fieldValue))
  }

}
