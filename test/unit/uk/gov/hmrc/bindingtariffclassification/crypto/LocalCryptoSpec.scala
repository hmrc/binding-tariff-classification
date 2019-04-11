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

package uk.gov.hmrc.bindingtariffclassification.crypto

import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.config.{AppConfig, MongoEncryption}
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.play.test.UnitSpec

class LocalCryptoSpec extends UnitSpec with MockitoSugar {

  private val config = mock[AppConfig]

  "encrypt()" should {

    "enable encrypt local" in {
      Mockito.when(config.mongoEncryption).thenReturn(MongoEncryption(true, Some("YjQ+NiViNGY4V2l2cSxnCg==")))
      (new LocalCrypto(config)).encrypt(PlainText("hello")).toString shouldBe "Crypted(gUfxIXsmMDAbdTgm36BmEg==)"
    }

    "error on missing config" in {
      Mockito.when(config.mongoEncryption).thenReturn(MongoEncryption(true, None))

      val caught = intercept[RuntimeException] {
        await(new LocalCrypto(config)).encrypt(PlainText("hello"))
      }
      caught.getMessage shouldBe "Missing config: 'mongodb.encryption.enabled'"
    }
  }

}
