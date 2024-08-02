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

package uk.gov.hmrc.bindingtariffclassification.connector

import com.codahale.metrics.Timer
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.model.{BankHoliday, BankHolidaySet, BankHolidaysResponse}
import uk.gov.hmrc.http.HttpReads

import java.time.LocalDate
import scala.concurrent.Future

class BankHolidaysConnectorSpec extends BaseSpec {

  trait ConnectorTestSetup {

    val timer: Timer.Context = mock[Timer.Context]

    object TestConnector extends BankHolidaysConnector(mockAppConfig, mockHttpClient, FakeHasMetrics)

    val fullURL = "https://www.gov.uk/bank-holidays.json"

    mockGetCall(fullURL)
    mockBankHolidayUrl(fullURL)
  }

  "BankHolidaysConnector" when {

    ".get()" when {

      "a successful response" should {

        "return dates of bank holidays" in new ConnectorTestSetup {

          val response: BankHolidaysResponse =
            BankHolidaysResponse(BankHolidaySet(Seq(BankHoliday(LocalDate.of(2020, 1, 1)))))

          when(mockRequestBuilder.execute(any[HttpReads[BankHolidaysResponse]], any()))
            .thenReturn(Future(response))

          val actual: Future[Set[LocalDate]] = TestConnector.get()
          val expected: Set[LocalDate]       = Set(LocalDate.of(2020, 1, 1))

          await(actual) shouldBe expected
        }
      }

      "a bad response" should {

        "recover and get a set of UK bank-holidays from the .gov website" in new ConnectorTestSetup {

          when(mockRequestBuilder.execute(any[HttpReads[BankHolidaysResponse]], any()))
            .thenReturn(Future.failed(new Throwable("help me")))

          val actual: Future[Set[LocalDate]] = TestConnector.get()

          await(actual).isEmpty shouldBe false
        }
      }
    }
  }
}
