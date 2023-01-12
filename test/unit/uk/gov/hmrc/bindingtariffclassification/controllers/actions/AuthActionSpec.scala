/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.controllers.actions
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, BodyParsers, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.model.bta.BtaRequest
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.auth.core.authorise.Predicate

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AuthActionSpec extends BaseSpec with BeforeAndAfterEach {

  private val authConnector = mock[AuthConnector]
  private val bodyParser    = mock[BodyParsers.Default]

  "AuthAction" should {

    val action                                          = new AuthAction(authConnector, bodyParser)
    val block: BtaRequest[AnyContent] => Future[Result] = { _ => Future.successful(Ok) }
    val atarEnrolment =
      Enrolments(Set(Enrolment("HMRC-ATAR-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB12345678")), "state")))
    val atarEnrolmentNoEori = Enrolments(Set(Enrolment("HMRC-ATAR-ORG", Seq.empty, "state")))
    val atarEnrolmentInvalidEori =
      Enrolments(Set(Enrolment("HMRC-ATAR-ORG", Seq(EnrolmentIdentifier("SomethingElse", "GB12345678")), "state")))
    val invalidEnrolment = Enrolments(Set(Enrolment("HMRC-ATAR-BORG", Seq.empty, "state")))
    val noEnrolments     = Enrolments(Set.empty)

    "return 200 given a valid enrolment and identifier" in {
      when(
        authConnector.authorise(any[Predicate], ArgumentMatchers.eq(Retrievals.allEnrolments))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.successful(atarEnrolment))

      val fakeRequest = FakeRequest().withHeaders(("Authorization", "Bearer Token"))

      whenReady(action.invokeBlock(fakeRequest, block))(res => res.header.status shouldBe OK)
    }

    "return 403 given a missing Authorization Header" in {
      val fakeRequest = FakeRequest()

      whenReady(action.invokeBlock(fakeRequest, block))(res => res.header.status shouldBe FORBIDDEN)
    }

    "return 403 given a valid enrolment but no identifier" in {
      when(
        authConnector.authorise(any[Predicate], ArgumentMatchers.eq(Retrievals.allEnrolments))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.successful(atarEnrolmentNoEori))

      val fakeRequest = FakeRequest().withHeaders(("Authorization", "Bearer Token"))

      whenReady(action.invokeBlock(fakeRequest, block))(res => res.header.status shouldBe FORBIDDEN)
    }

    "return 403 given an invalid eori" in {
      when(
        authConnector.authorise(any[Predicate], ArgumentMatchers.eq(Retrievals.allEnrolments))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.successful(atarEnrolmentInvalidEori))

      val fakeRequest = FakeRequest().withHeaders(("Authorization", "Bearer Token"))

      whenReady(action.invokeBlock(fakeRequest, block))(res => res.header.status shouldBe FORBIDDEN)
    }

    "return 403 given an invalid enrolment" in {
      when(
        authConnector.authorise(any[Predicate], ArgumentMatchers.eq(Retrievals.allEnrolments))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.successful(invalidEnrolment))

      val fakeRequest = FakeRequest().withHeaders(("Authorization", "Bearer Token"))

      whenReady(action.invokeBlock(fakeRequest, block))(res => res.header.status shouldBe FORBIDDEN)
    }

    "return 403 given no enrolments" in {
      when(
        authConnector.authorise(any[Predicate], ArgumentMatchers.eq(Retrievals.allEnrolments))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.successful(noEnrolments))

      val fakeRequest = FakeRequest().withHeaders(("Authorization", "Bearer Token"))

      whenReady(action.invokeBlock(fakeRequest, block))(res => res.header.status shouldBe FORBIDDEN)
    }

    "return 403 given any other Auth failure" in {
      when(
        authConnector.authorise(any[Predicate], ArgumentMatchers.eq(Retrievals.allEnrolments))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.failed(UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)))

      val fakeRequest = FakeRequest().withHeaders(("Authorization", "Bearer Token"))

      whenReady(action.invokeBlock(fakeRequest, block))(res => res.header.status shouldBe FORBIDDEN)
    }
  }

}
