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

import org.mongodb.scala.bson.{BsonArray, BsonDocument}
import play.api.libs.json._
import play.api.libs.json.Json.JsValueWrapper

trait Mapper {
  def reference(reference: String): JsObject =
    Json.obj("reference" -> reference)

  def field[A: Writes](fieldName: String, fieldValue: A): Seq[(String, JsValueWrapper)] =
    Seq(fieldName -> Json.toJson(fieldValue))

  def updateField(fieldName: String, fieldValue: JsValue): JsObject =
    Json.obj("$set" -> Json.obj(fieldName -> fieldValue))

  def updateField[A: Writes](fieldName: String, fieldValue: A): JsObject =
    Json.obj("$set" -> Json.obj(field(fieldName, fieldValue): _*))

  def updateFields(fields: Seq[BsonDocument]): BsonDocument =
    BsonDocument("$set" -> BsonArray.fromIterable(fields))
}
