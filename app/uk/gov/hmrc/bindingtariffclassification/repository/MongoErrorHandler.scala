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

trait MongoErrorHandler {

  private def hasErrors(wr: WriteResult): Boolean = {
    wr match {
      case uwr: UpdateWriteResult =>
        !uwr.ok ||
          uwr.errmsg.isDefined ||
          uwr.writeConcernError.isDefined ||
          uwr.writeErrors.nonEmpty
      case _ =>
        !wr.ok ||
          wr.writeConcernError.isDefined ||
          wr.writeErrors.nonEmpty
    }
  }

  def handleUpsertError(wResult: WriteResult, errMsg: => String): Boolean = {
    if (hasErrors(wResult)) {
      throwDbError(errMsg, wResult)
    } else {
      wResult match {
        case upw: UpdateWriteResult =>
          if (isDatabaseAltered(wResult)) upw.upserted.nonEmpty
          else throwDbError(errMsg, wResult)
        case _ => true
      }
    }
  }

  private def throwDbError(errMsg: => String, wResult: WriteResult)= {
    throw new RuntimeException(s"$errMsg. Reason: $wResult")
  }

  private def isDatabaseAltered(writeResult: WriteResult): Boolean = {
    writeResult.n > 0
  }

}
