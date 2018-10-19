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

package it.uk.gov.hmrc.stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalTo, post, urlPathEqualTo}
import it.uk.gov.hmrc.component.MockHost
import play.api.libs.json.Json
import uk.gov.hmrc.bindingtariffclassification.model.Case
import play.api.http.HeaderNames

object CaseStub extends MockHost(14681) {

  import uk.gov.hmrc.bindingtariffclassification.model.JsonFormatters._

  def postCase(c: Case) = {
    mock.register(
      post(urlPathEqualTo("/cases"))
        .withHeader(HeaderNames.USER_AGENT, equalTo(userAgent))
        .willReturn(aResponse()
          .withStatus(201)
          .withBody(Json.toJson(c).toString())))
  }

}