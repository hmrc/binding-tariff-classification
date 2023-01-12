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

package uk.gov.hmrc.bindingtariffclassification.crypto

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.crypto.{AesCrypto, CompositeSymmetricCrypto, Decrypter}

@Singleton
class LocalCrypto @Inject() (appConfig: AppConfig) extends CompositeSymmetricCrypto {

  override protected lazy val currentCrypto: AesCrypto = new AesCrypto {
    override protected lazy val encryptionKey: String = {
      appConfig.mongoEncryption.key match {
        case Some(k) if appConfig.mongoEncryption.enabled => k
        case _                                            => throw new RuntimeException("Missing config: 'mongodb.encryption.enabled'")
      }
    }
  }

  override protected val previousCryptos = Seq.empty[Decrypter]

}
