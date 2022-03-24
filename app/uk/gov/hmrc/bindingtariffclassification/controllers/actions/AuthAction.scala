/*
 * Copyright 2022 HM Revenue & Customs
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

import com.google.inject.Inject
import play.api.Logging
import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.bindingtariffclassification.model.bta.BtaRequest
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, HeaderNames}
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class AuthAction @Inject()(override val authConnector: AuthConnector,
                           val parser: BodyParsers.Default)(implicit val executionContext: ExecutionContext)
  extends ActionBuilder[BtaRequest, AnyContent] with ActionFunction[Request, BtaRequest] with AuthorisedFunctions with Logging {

  private lazy final val ATAR_ENROLMENT_KEY = "HMRC-ATAR-ORG"
  private lazy final val EORI_IDENTIFIER = "EORINumber"

  override def invokeBlock[A](request: Request[A], block: BtaRequest[A] => Future[Result]): Future[Result] = {
    request.headers.get(HeaderNames.authorisation) match {
      case Some(authHeader) =>
        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
          .copy(authorization = Some(Authorization(authHeader)))
        retrievalData(request, block)
      case _ =>
        logger.warn(s"[AuthAction][invokeBlock] An error occurred during auth action: No Authorization header provided")
        Future.successful(Forbidden)
    }
  }

  private def retrievalData[A](request: Request[A], block: BtaRequest[A] => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {
    getEnrolmentsAndEori().flatMap {
      case Right(eori) => block(BtaRequest(request, eori))
      case Left(_) => Future.successful(Forbidden)
    }
  }

  private def getEnrolmentsAndEori(retrieval: Retrieval[Enrolments] = Retrievals.authorisedEnrolments)
                                  (implicit hc: HeaderCarrier): Future[Either[Boolean, String]] = {
    def retry = {
      if(retrieval == Retrievals.allEnrolments){
        logger.warn(s"[AuthAction][getEnrolmentsAndEori] An error occurred during auth action: Fallback to All Enrolments failed")
        Future.successful(Left(true))
      } else {
        logger.warn(s"[AuthAction][getEnrolmentsAndEori] An error occurred during auth action: Falling back to All Enrolments")
        getEnrolmentsAndEori(Retrievals.allEnrolments)
      }
    }
    authorised().retrieve(retrieval) { enrolments =>
      val maybeEnrolment = enrolments.getEnrolment(ATAR_ENROLMENT_KEY)
      val maybeEori = maybeEnrolment.flatMap(_.getIdentifier(EORI_IDENTIFIER).map(_.value))
      (maybeEnrolment, maybeEori) match {
        case (Some(_), Some(eori)) => Future.successful(Right(eori))
        case (Some(_), None) => logger.warn(s"[AuthAction][getEnrolmentsAndEori] An error occurred during auth action: Missing Identifier")
          retry
        case _ => logger.warn(s"[AuthAction][getEnrolmentsAndEori] An error occurred during auth action: Missing Enrolment")
          retry
      }
    }.recoverWith {
      case e => logger.error(s"[AuthAction][getEnrolmentsAndEori] An exception occurred during auth action: ${e.getMessage}", e)
        retry
    }
  }

}
