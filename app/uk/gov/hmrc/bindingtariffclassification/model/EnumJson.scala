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

package uk.gov.hmrc.bindingtariffclassification.model

import play.api.libs.json._

object EnumJson {

  implicit def format[E <: Enumeration](enum: E): Format[E#Value] = {
    Format(Reads.enumNameReads(enum), Writes.enumNameWrites)
  }

  def readsMap[E, B](implicit erds: Reads[E], brds: Reads[B]): JsValue => JsResult[Map[E, B]] = (js: JsValue) => {
    val maprds: Reads[Map[String, B]] = Reads.mapReads[B]
    Json.fromJson[Map[String, B]](js)(maprds).map(_.map {
      case (key: String, value: B) => erds.reads(JsString(key)).get -> value
    })
  }

  def writesMap[E, B](implicit ewrts: Writes[E], bwrts: Writes[B]): Map[E, B] => JsObject = (map: Map[E, B]) =>
    Json.toJson(map.map {
      case (group, value) => group.toString -> value
    }).as[JsObject]

  def formatMap[E, B](implicit efmt: Format[E], bfmt: Format[B]): OFormat[Map[E, B]] = OFormat(
    read = readsMap(efmt, bfmt),
    write = writesMap(efmt, bfmt)
  )

}
