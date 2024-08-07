/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.controllers

import org.mockito.Mockito.when
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.migrations.{AddKeywordsMigrationJob, AmendDateOfExtractMigrationJob, MigrationRunner, RollbackFailure}
import uk.gov.hmrc.http.HttpVerbs

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Future.{failed, successful}

class MigrationControllerSpec extends BaseSpec {

  private val runner                             = mock[MigrationRunner]
  private val mockAddKeywordsMigrationJob        = mock[AddKeywordsMigrationJob]
  private val mockAmendDateOfExtractMigrationJob = mock[AmendDateOfExtractMigrationJob]

  private val fakeRequest = FakeRequest(method = HttpVerbs.PUT, path = "/scheduler/days-elapsed")

  private val controller =
    new MigrationController(runner, mcc, mockAddKeywordsMigrationJob, mockAmendDateOfExtractMigrationJob)

  "Amend Date Of Extract" should {

    "return 204 when the runner executes successfully" in {
      when(runner.trigger(mockAmendDateOfExtractMigrationJob)).thenReturn(Future.successful(Some(RollbackFailure)))

      val result = controller.amendDateOfExtract()(fakeRequest).futureValue
      result.header.status shouldBe NO_CONTENT
    }

    "return 500 when an error occurred" in {
      when(runner.trigger(mockAmendDateOfExtractMigrationJob)).thenReturn(failed(new RuntimeException))

      val result = controller.amendDateOfExtract()(fakeRequest)

      status(result)                   shouldBe INTERNAL_SERVER_ERROR
      contentAsJson(result).toString() shouldBe """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

}
