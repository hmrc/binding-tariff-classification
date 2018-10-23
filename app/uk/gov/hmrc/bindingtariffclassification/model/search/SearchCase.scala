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

package uk.gov.hmrc.bindingtariffclassification.model.search

import play.api.libs.json.{JsNull, JsObject, JsString, JsValue}

case class SearchCase(
                       reference: Option[String] = None,
                       queueId: Option[String] = None,
                       assigneeId: Option[String] = None
                     ) {
  // Basic functionality of this class is to encapsulate all the parameters

  def buildJson: JsObject = {

    def noneOrValue: String => JsValue = { v: String =>
      if (v.toLowerCase == "none") JsNull
      else JsString(v)
    }

    JsObject(
      Seq[(String, JsValue)]() ++
        reference.map("reference" -> JsString(_)) ++
        queueId.map("queueId" -> noneOrValue(_)) ++
        assigneeId.map("assigneeId" -> noneOrValue(_))
    )
  }
}


case class SearchCaseBuilder() {

  // TODO: we should not use var !
  private var reference: Option[String] = None
  private var queueId: Option[String] = None
  private var assigneeId: Option[String] = None

  def withReference(reference: String) = {
    this.reference = Some(reference)
    this
  }

  def withQueueId(queueId: Option[String]) = {
    this.queueId = queueId
    this
  }

  def withAssigneeId(assigneeId: Option[String]) = {
    this.assigneeId = assigneeId
    this
  }

  def build() = SearchCase(reference, queueId, assigneeId)

}

object SearchCaseBuilder extends SearchCaseBuilder
