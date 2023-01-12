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

package uk.gov.hmrc.bindingtariffclassification.filters

import akka.stream.Materializer
import play.api.mvc.{Filter, RequestHeader, Result, Results}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class AuthFilter @Inject() (appConfig: AppConfig)(implicit override val mat: Materializer) extends Filter {

  private val healthEndpointUri = "/ping/ping"
  private val btaCardEndpoint   = "/bta-card"
  private val authToken         = "X-Api-Token"

  override def apply(f: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] =
    if (isRequestExcludedFromAPIToken(rh)) f(rh) else ensureAuthTokenIsPresent(f, rh)

  private def ensureAuthTokenIsPresent(f: RequestHeader => Future[Result], rh: RequestHeader): Future[Result] =
    rh.headers.get(authToken) match {
      case Some(appConfig.authorization) => f(rh)
      case _                             => Future.successful(Results.Forbidden(s"Missing or invalid '$authToken'"))
    }

  private def isRequestExcludedFromAPIToken(requestHeader: RequestHeader): Boolean =
    requestHeader.uri.endsWith(healthEndpointUri) || requestHeader.path.endsWith(btaCardEndpoint)
}
