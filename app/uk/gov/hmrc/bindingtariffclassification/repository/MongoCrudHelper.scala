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

import play.api.libs.json.{JsObject, OFormat, OWrites}
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.bindingtariffclassification.model.IsInsert

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MongoCrudHelper[T] extends MongoIndexCreator with MongoErrorHandler {

  protected val mongoCollection: JSONCollection

  def saveAtomic(selector: JsObject, updateOperations: JsObject)(implicit w: OFormat[T]): Future[(T, IsInsert)] = {
    val updateOp = mongoCollection.updateModifier(
      update = updateOperations,
      fetchNewObject = true,
      upsert = true
    )

    mongoCollection.findAndModify(selector, updateOp).map { findAndModifyResult =>
      val maybeTuple: Option[(T, IsInsert)] = for {
        value <- findAndModifyResult.value
        updateLastError <- findAndModifyResult.lastError
      } yield (value.as[T], !updateLastError.updatedExisting)

      maybeTuple.fold[(T, IsInsert)] {
        handleError(selector, findAndModifyResult)
      }(tuple => tuple)
    }
  }

  private def handleError(selector: JsObject, findAndModifyResult: mongoCollection.BatchCommands.FindAndModifyCommand.FindAndModifyResult) = {
    val error = s"Error upserting database for $selector."
    throw new RuntimeException(s"$error lastError: ${findAndModifyResult.lastError}")
  }

  def save(entity: T, selector: JsObject)(implicit w: OWrites[T]): Future[(T, IsInsert)] = {
    mongoCollection.update(selector, entity, upsert = true).map {
      updateWriteResult => (entity, handleSaveError(updateWriteResult, s"Could not save entity: $entity"))
    }
  }
}