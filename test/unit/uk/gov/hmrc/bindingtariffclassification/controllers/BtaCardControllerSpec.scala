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

package uk.gov.hmrc.bindingtariffclassification.controllers
import akka.japi.Option.Some
import com.google.inject.Inject
import org.mockito.Mockito.when
import play.api.inject.Injector
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.controllers.actions.AuthAction
import uk.gov.hmrc.bindingtariffclassification.model.bta.{BtaApplications, BtaCard, BtaRulings}
import uk.gov.hmrc.bindingtariffclassification.service.BtaCardService
import uk.gov.hmrc.http.{HeaderCarrier, HttpVerbs}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

// scalastyle:off magic.number
class BtaCardControllerSpec extends BaseSpec {

  private val eori                          = "GB123"
  lazy val injector: Injector               = app.injector
  lazy val bodyParsers: BodyParsers.Default = injector.instanceOf[BodyParsers.Default]
  private val btaCardService                = mock[BtaCardService]
  def fakeResponse: Enrolments =
    Enrolments(Set(Enrolment("HMRC-ATAR-ORG", Seq(EnrolmentIdentifier("EORINumber", eori)), "active")))
  class FakeSuccessAuthConnector[B] @Inject() (response: B) extends AuthConnector {
    override def authorise[A](
      predicate: Predicate,
      retrieval: Retrieval[A]
    )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
      Future.successful(response.asInstanceOf[A])
  }
  class Authorised[B](response: B, bodyParser: BodyParsers.Default)
      extends AuthAction(new FakeSuccessAuthConnector[B](response), bodyParser)
  object AuthorisedAction extends Authorised[Enrolments](fakeResponse, bodyParsers)
  private val controller = new BtaCardController(btaCardService, AuthorisedAction, mcc)
  private val request =
    FakeRequest(method = HttpVerbs.GET, uri = "/bta-card", headers = Headers(("Authorization", "auth")), body = "")

  "getBtaCard" should {

    "return 200 with a Json body if successful" in {
      when(btaCardService.generateBtaCard(eori))
        .thenReturn(Future.successful(BtaCard(eori, Some(BtaApplications(1, 1)), Some(BtaRulings(2, 2)))))
      val result = controller.getBtaCard(request)

      status(result) shouldBe OK
      contentAsJson(result).toString() shouldBe
        s"""{"eori":"GB123","applications":{"total":1,"actionable":1},"rulings":{"total":2,"expiring":2}}""".stripMargin
    }

    "return 500 if unsuccessful" in {
      when(btaCardService.generateBtaCard(eori)).thenReturn(Future.failed(new Exception("error")))
      val result = controller.getBtaCard(request)

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }

}
