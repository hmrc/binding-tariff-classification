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

package uk.gov.hmrc.bindingtariffclassification.base

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.{ArgumentMatchers, Mockito}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

trait MockHttpClient {

  val mockHttpClient: HttpClientV2       = Mockito.mock(classOf[HttpClientV2])
  val mockRequestBuilder: RequestBuilder = Mockito.mock(classOf[RequestBuilder])
  val mockAppConfig: AppConfig           = Mockito.mock(classOf[AppConfig])

  def mockBankHolidayUrl(url: String): Unit =
    when(mockAppConfig.bankHolidaysUrl).thenReturn(url)

  def mockFileStoreUrl(url: String): Unit =
    when(mockAppConfig.fileStoreUrl).thenReturn(url)

  def mockGetCall(fullURL: String): Unit = {
    when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
    when(mockHttpClient.get(ArgumentMatchers.eq(url"$fullURL"))(any[HeaderCarrier]())).thenReturn(mockRequestBuilder)
  }

  def mockDeleteCall(fullURL: String): Unit = {
    when(mockRequestBuilder.withBody(any())(any(), any(), any())).thenReturn(mockRequestBuilder)
    when(mockHttpClient.delete(ArgumentMatchers.eq(url"$fullURL"))(any[HeaderCarrier]())).thenReturn(mockRequestBuilder)
  }

}
