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

package unit.uk.gov.hmrc.bindingtariffclassification.repository

import reactivemongo.api.commands._
import reactivemongo.bson.BSONInteger
import uk.gov.hmrc.bindingtariffclassification.repository.MongoErrorHandler
import uk.gov.hmrc.play.test.UnitSpec

class MongoErrorHandlerSpec extends UnitSpec {

  private val mongoErrorHandler = new MongoErrorHandler {}
  private val BsonValue = BSONInteger(1)

  private val writeErrors = Seq(WriteError(123, 44005, "mongo conflict at index 451"))
  private val SomeWriteConcernError = Some(WriteConcernError(1, "ERROR"))
  private val SomeError = Some("The database suddenly exploded!")
  private val SomeCode = Some(123456)

  "handleSaveError" should {

    "return true if there are no database errors and at least one record inserted" in {
      val Inserted = Seq(Upserted(0, BsonValue))
      val successfulInsertWriteResult = updateWriteResult(alteredRecords = 1, upserted = Inserted)

      mongoErrorHandler.handleUpsertError(successfulInsertWriteResult, "") shouldBe true
    }

    "return false if there are no database errors and no record inserted and at least one record updated" in {
      val successfulUpdateWriteResult = updateWriteResult(alteredRecords = 1)

      mongoErrorHandler.handleUpsertError(successfulUpdateWriteResult, "") shouldBe false
    }

    "throw a RuntimeException if there is a database error" in {
      val err = updateWriteResult(
        alteredRecords = 0,
        errMsg = SomeError,
        code = SomeCode,
        writeErrors = writeErrors,
        writeConcernError = SomeWriteConcernError
      )

      val caught = intercept[RuntimeException](mongoErrorHandler.handleUpsertError(err, "Cannot insert or update document"))

      caught.getMessage shouldBe "Cannot insert or update document. Reason: " +
        "UpdateWriteResult(true,0,0,List()," +
        "List(WriteError(123,44005,mongo conflict at index 451))," +
        "Some(WriteConcernError(1,ERROR))," +
        "Some(123456)," +
        "Some(The database suddenly exploded!))"
    }

    "throw a RuntimeException if there are no records altered" in {
      val err = updateWriteResult(alteredRecords = 0)

      val caught = intercept[RuntimeException](mongoErrorHandler.handleUpsertError(err, "Cannot insert or update document"))

      caught.getMessage shouldBe "Cannot insert or update document. Reason: " +
        "UpdateWriteResult(true,0,0,List(),List(),None,None,None)"
    }

  }

  private def updateWriteResult(alteredRecords: Int,
                                upserted: Seq[Upserted] = Nil,
                                writeErrors: Seq[WriteError] = Nil,
                                writeConcernError: Option[WriteConcernError] = None,
                                code: Option[Int] = None,
                                errMsg: Option[String] = None): UpdateWriteResult = {
    UpdateWriteResult(
      ok = true,
      n = alteredRecords,
      nModified = 0,
      upserted = upserted,
      writeErrors = writeErrors,
      writeConcernError = writeConcernError,
      code = code,
      errmsg = errMsg)
  }

}
