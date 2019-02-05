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

import java.util.UUID

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.search.{Filter, Search}
import uk.gov.hmrc.crypto.{CompositeSymmetricCrypto, Crypted, PlainText}
import uk.gov.hmrc.play.test.UnitSpec
import util.CaseData._

class CryptoSpec extends UnitSpec with MockitoSugar {

  private val simmetricCrypto = mock[CompositeSymmetricCrypto]
  private val crypto = new Crypto(simmetricCrypto)

  private def encEori(k: String) = EORIDetails(k, k, k, k, k, k, k)
  private def encContacts(k: String) = Contact(k, k, Some(k))

  private val bti = createBTIApplicationWithAllFields
  private val lo = createLiabilityOrder
  private val filter = Filter(traderName = Some("name"))

  private def expectedEncryptedBti(k: String, letter: Option[Attachment]): BTIApplication = {
    bti.copy(
      holder = encEori(k),
      contact = encContacts(k),
      agent = Some(AgentDetails(encEori(k), letter)),
      confidentialInformation = Some(k)
    )
  }

  private def expectedEncryptedLiabilityOrder(k: String): LiabilityOrder = {
    lo.copy(
      holder = encEori(k),
      contact = encContacts(k)
    )
  }

  private def expectedEncryptedSearchFilter(k: String): Filter = {
    filter.copy(traderName = Some(k))
  }

  "encrypt()" should {

    val k = UUID.randomUUID().toString
    Mockito.when(simmetricCrypto.encrypt(any[PlainText]())).thenReturn(Crypted(k))

    "encrypt BTI applications" in {
      val c = createCase(app = bti)
      val enc = crypto.encrypt(c)
      enc shouldBe c.copy(application = expectedEncryptedBti(k, c.application.asInstanceOf[BTIApplication].agent.get.letterOfAuthorisation))
    }

    "encrypt Liability orders" in {
      val c = createCase(app = lo)
      val enc = crypto.encrypt(c)
      enc shouldBe c.copy(application = expectedEncryptedLiabilityOrder(k))
    }

    "encrypt Search" in {
      val search: Search = Search(filter = filter)
      val enc: Search = crypto.encrypt(search)
      enc shouldBe search.copy(filter = expectedEncryptedSearchFilter(k))

    }

  }

  "decrypt()" should {

    val k = UUID.randomUUID().toString
    Mockito.when(simmetricCrypto.decrypt(any[Crypted]())).thenReturn(PlainText(k))

    "decrypt BTI applications" in {
      val c = createCase(app = bti)
      val dec = crypto.decrypt(c)
      dec shouldBe c.copy(application = expectedEncryptedBti(k, c.application.asInstanceOf[BTIApplication].agent.get.letterOfAuthorisation))
    }

    "decrypt Liability orders" in {
      val c = createCase(app = lo)
      val dec = crypto.decrypt(c)
      dec shouldBe c.copy(application = expectedEncryptedLiabilityOrder(k))
    }

  }

}
