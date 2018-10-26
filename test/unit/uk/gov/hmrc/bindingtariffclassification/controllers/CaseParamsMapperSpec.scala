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

package uk.gov.hmrc.bindingtariffclassification.controllers

import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.play.test.UnitSpec

class CaseParamsMapperSpec extends UnitSpec with MockitoSugar {

  val mapper = new CaseParamsMapper()

  "Case Params Mapper" should {
    "map queueId" in {
      mapper.from(Some("id"), None, None).queueId shouldBe Some("id")
    }

    "map assigneeId" in {
      mapper.from(None, Some("id"), None).assigneeId shouldBe Some("id")
    }

    "map status" in {
      mapper.from(None, None, Some("status")).status.get should contain only "status"
    }

    "map multiple statuses" in {
      mapper.from(None, None, Some("status1, status2")).status.get should contain only ("status1", "status2")
    }
  }

}
