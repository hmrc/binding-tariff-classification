/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification

import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.bindingtariffclassification.repository.{CaseMongoRepository, CaseRepository, EncryptedCaseMongoRepository}
import uk.gov.hmrc.play.test.UnitSpec

class ModuleTest extends UnitSpec {

  private def injector(conf: (String, Any)*): Injector = new GuiceApplicationBuilder()
    .bindings(new Module)
    .configure(conf: _*)
    .injector()

  "Module 'bind" should {
    "Bind encryption repository" in {
      injector("mongodb.encryption.enabled" -> true)
        .instanceOf[CaseRepository].isInstanceOf[EncryptedCaseMongoRepository] shouldBe true
    }

    "Bind standard repository" in {
      injector("mongodb.encryption.enabled" -> false)
        .instanceOf[CaseRepository].isInstanceOf[CaseMongoRepository] shouldBe true
    }

    "Bind standard repository by default" in {
      injector()
        .instanceOf[CaseRepository].isInstanceOf[CaseMongoRepository] shouldBe true
    }
  }
}
