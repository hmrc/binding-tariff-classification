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

import com.google.inject.ImplementedBy
import javax.inject.Singleton
import play.api.libs.json.{JsNull, JsObject, JsString, JsValue}
import uk.gov.hmrc.bindingtariffclassification.model.search.{CaseParamsFilter, FilterMapper}

@ImplementedBy(classOf[CaseParamsMapper])
trait FilterParamsMapper {
  def from: CaseParamsFilter => JsObject
}

@Singleton
class CaseParamsMapper extends FilterParamsMapper {

  private val NONE = "none"

  override def from: CaseParamsFilter => JsObject = searchCase => {

    def noneOrValue: String => JsValue = { v: String =>
      if (v.toLowerCase == NONE) JsNull
      else JsString(v)
    }

    JsObject(
      Seq[(String, JsValue)]() ++
        searchCase.reference.map("reference" -> JsString(_)) ++
        searchCase.queueId.map("queueId" -> noneOrValue(_)) ++
        searchCase.assigneeId.map("assigneeId" -> noneOrValue(_))
    )
  }
}
