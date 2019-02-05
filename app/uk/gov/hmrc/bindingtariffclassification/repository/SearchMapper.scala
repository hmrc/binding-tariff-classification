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
import uk.gov.hmrc.bindingtariffclassification.model.search.{Filter, Sort}
import uk.gov.hmrc.bindingtariffclassification.model.sort.SortField
import uk.gov.hmrc.bindingtariffclassification.model.sort.SortField.SortField

@Singleton
class SearchMapper {

  def filterBy(filter: Filter): JsObject = {
    JsObject(
      Map() ++
        filter.queueId.map("queueId" -> nullifyNoneValues(_)) ++
        filter.assigneeId.map("assignee.id" -> nullifyNoneValues(_)) ++
        filter.statuses.map(statuses => "status" -> array[CaseStatus](statuses)) ++
        filter.traderName.map("application.holder.businessName" -> nullifyNoneValues(_))
    )
  }


  def sortBy(sort: Sort): JsObject = {
    Json.obj(toMongoField(sort.field) -> sort.direction.id)
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

  private def array[T](values: TraversableOnce[T])(implicit writes: Writes[T]): JsObject = {
    JsObject(Map("$in" -> JsArray(values.toSeq.map(writes.writes))))
  }

  private def nullifyNoneValues: String => JsValue = { v: String =>
    v match {
      case "none" => JsNull
      case _ => JsString(v)
    }
  }

  private def notEqualFilter(field: String): JsObject = {
    Json.obj("$ne" -> field)
  }

  private def toMongoField(sort: SortField): String = {
    sort match {
      case SortField.DAYS_ELAPSED => "daysElapsed"
      case unknown => throw new IllegalArgumentException(s"cannot sort by field: $unknown")
    }
  }

}
