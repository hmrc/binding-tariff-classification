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

import reactivemongo.api.commands.{UpdateWriteResult, WriteResult}
import uk.gov.hmrc.bindingtariffclassification.model.IsInsert

trait MongoErrorHandler {

  def handleSaveError(updateWriteResult: UpdateWriteResult, exceptionMsg: => String): IsInsert = {

    def handleUpsertError(result: WriteResult) =
      if (databaseAltered(result)) {
        updateWriteResult.upserted.nonEmpty
      } else {
        throw new RuntimeException(exceptionMsg)
      }

    handleError(updateWriteResult, handleUpsertError, exceptionMsg)
  }

  private def handleError(result: WriteResult, f: WriteResult => Boolean, exceptionMsg: String): Boolean = {
    result.writeConcernError.fold(f(result)) {
      errMsg => throw new RuntimeException(s"""$exceptionMsg. $errMsg""")
    }
  }

  private def databaseAltered(writeResult: WriteResult): Boolean = writeResult.n > 0
}
