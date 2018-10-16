package it.uk.gov.hmrc.stubs

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalTo, post, urlPathEqualTo}
import it.uk.gov.hmrc.component.MockHost
import play.api.libs.json.Json
import uk.gov.hmrc.bindingtariffclassification.model.Case
import play.api.http.HeaderNames

object CaseStub extends MockHost(14681) {

  import uk.gov.hmrc.bindingtariffclassification.model.JsonFormatters._

  def postCase(caseModel: Case) = {
    mock.register(
      post(urlPathEqualTo("/cases"))
        .withHeader(HeaderNames.USER_AGENT, equalTo(userAgent))
        .willReturn(aResponse()
          .withStatus(201)
          .withBody(Json.toJson((caseModel)).toString())))
  }

}