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

import java.util.UUID

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.crypto.{CompositeSymmetricCrypto, Crypted, PlainText}
import util.CaseData._

class CryptoSpec extends BaseSpec {

  private val simmetricCrypto = mock[CompositeSymmetricCrypto]
  private val crypto          = new Crypto(simmetricCrypto)

  private def encEori(k: String)      = EORIDetails(k, "John Lewis", k, k, k, k, k)
  private def encAgentEori(k: String) = EORIDetails(k, "Frank Agent-Smith", k, k, k, k, k)
  private def encContacts(k: String)  = Contact(k, k, Some(k))
  private def encAddress(k: String)   = Address(k, k, Some(k), Some(k))

  private val bti    = createBTIApplicationWithAllFields()
  private val lo     = createLiabilityOrder
  private val misc   = createMiscApplication
  private val corres = createCorrespondenceApplication

  private def expectedEncryptedBti(k: String, letter: Option[Attachment]): BTIApplication =
    bti.copy(
      holder                  = encEori(k),
      contact                 = encContacts(k),
      agent                   = Some(AgentDetails(encAgentEori(k), letter)),
      confidentialInformation = Some(k)
    )

  private def expectedEncryptedLiabilityOrder(k: String): LiabilityOrder =
    lo.copy(
      contact = encContacts(k)
    )

  private def expectedEncryptedMiscApplication(k: String): MiscApplication =
    misc.copy(
      contact     = encContacts(k),
      contactName = Some(k),
      name        = k
    )

  private def expectedEncryptedCorrespondenceApplication(k: String): CorrespondenceApplication =
    corres.copy(
      contact   = encContacts(k),
      agentName = Some(k),
      address   = encAddress(k)
    )

  "encrypt()" should {

    val k = UUID.randomUUID().toString
    Mockito.when(simmetricCrypto.encrypt(any[PlainText]())).thenReturn(Crypted(k))

    "encrypt BTI applications" in {
      val c   = createCase(app = bti)
      val enc = crypto.encrypt(c)
      enc shouldBe c.copy(application = expectedEncryptedBti(k, c.application.asBTI.agent.get.letterOfAuthorisation))
    }

    "encrypt Liability orders" in {
      val c   = createCase(app = lo)
      val enc = crypto.encrypt(c)
      enc shouldBe c.copy(application = expectedEncryptedLiabilityOrder(k))
    }

    "encrypt Miscellaneous applications" in {
      val c   = createCase(app = misc)
      val enc = crypto.encrypt(c)
      enc shouldBe c.copy(application = expectedEncryptedMiscApplication(k))
    }

    "encrypt Correspondence applications" in {
      val c   = createCase(app = corres)
      val enc = crypto.encrypt(c)
      enc shouldBe c.copy(application = expectedEncryptedCorrespondenceApplication(k))
    }

  }

  "decrypt()" should {

    val k = UUID.randomUUID().toString
    Mockito.when(simmetricCrypto.decrypt(any[Crypted]())).thenReturn(PlainText(k))

    "decrypt BTI applications" in {
      val c   = createCase(app = bti)
      val dec = crypto.decrypt(c)
      dec shouldBe c.copy(application = expectedEncryptedBti(k, c.application.asBTI.agent.get.letterOfAuthorisation))
    }

    "decrypt Liability orders" in {
      val c   = createCase(app = lo)
      val dec = crypto.decrypt(c)
      dec shouldBe c.copy(application = expectedEncryptedLiabilityOrder(k))
    }

    "decrypt Misc applications" in {
      val c   = createCase(app = misc)
      val dec = crypto.decrypt(c)
      dec shouldBe c.copy(application = expectedEncryptedMiscApplication(k))
    }

    "decrypt Correspondence applications" in {
      val c   = createCase(app = corres)
      val dec = crypto.decrypt(c)
      dec shouldBe c.copy(application = expectedEncryptedCorrespondenceApplication(k))
    }

  }

  "encrypt string()" should {

    "encrypt the string " in {
      val s   = " a string"
      val enc = crypto.encryptString(s)
      enc shouldNot equal(s)
    }

  }

}
