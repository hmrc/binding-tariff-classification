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

package uk.gov.hmrc.bindingtariffclassification.component.utils

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status.{OK, UNAUTHORIZED}
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}

object AuthStub extends WireMockMethods {

  private val authoriseUri                                    = "/auth/authorise"
  implicit val identifierFormat: OFormat[EnrolmentIdentifier] = Json.format[EnrolmentIdentifier]
  implicit val enrolmentFormat: OFormat[Enrolment]            = Json.format[Enrolment]
  case class AllEnrolments(allEnrolments: Set[Enrolment])
  implicit val enrolmentsFormat: OFormat[AllEnrolments] = Json.format[AllEnrolments]

  def authorised(): StubMapping =
    when(method = POST, uri = authoriseUri)
      .thenReturn(
        status = OK,
        body   = AllEnrolments(Set(Enrolment("HMRC-ATAR-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB123")), "active")))
      )

  def unauthorised(): StubMapping =
    when(method = POST, uri = authoriseUri).thenReturn(status = UNAUTHORIZED)
}
