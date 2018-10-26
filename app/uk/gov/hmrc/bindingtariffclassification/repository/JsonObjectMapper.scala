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
import uk.gov.hmrc.bindingtariffclassification.model.search.CaseParamsFilter

@Singleton
class JsonObjectMapper {

  private def nullifyNoneValues: String => JsValue = { v: String =>
    v match {
      case "none" => JsNull
      case _ => JsString(v)
    }
  }

  private def toJSArray: Seq[String] => JsArray = {
    list => JsArray(list.map(element => JsString(element)))
  }

  def from: CaseParamsFilter => JsObject = searchCase => {
    val queueFilter = searchCase.queueId.map("queueId" -> nullifyNoneValues(_))
    val assigneeFilter = searchCase.assigneeId.map("assigneeId" -> nullifyNoneValues(_))
    val statusFilter = searchCase.status
      .map(toJSArray(_))
      .map(array => JsObject(Map("$in" -> array)))
      .map("status" -> _)
    JsObject(Map() ++ queueFilter ++ assigneeFilter ++ statusFilter)
  }

  def fromReference(reference: String): JsObject = {
    Json.obj("reference" -> reference)
  }


}
