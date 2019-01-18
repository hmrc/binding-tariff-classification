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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.model.{AgentDetails, BTIApplication, Contact, EORIDetails}
import uk.gov.hmrc.crypto.{CompositeSymmetricCrypto, Crypted, PlainText}
import uk.gov.hmrc.play.test.UnitSpec
import util.CaseData._

class CryptoSpec extends UnitSpec with MockitoSugar {

  private val simmetricCrypto = mock[CompositeSymmetricCrypto]
  private val crypto = new Crypto(simmetricCrypto)

  private val encEori = EORIDetails("XYZ", "XYZ", "XYZ", "XYZ", "XYZ", "XYZ", "XYZ")
  private val encContacts = Contact("XYZ", "XYZ", Some("XYZ"))

  private val bti = createBTIApplicationWithAllFields
  private val lo = createLiabilityOrder

  "encrypt()" should {

    Mockito.when(simmetricCrypto.encrypt(any[PlainText]())).thenReturn(Crypted("XYZ"))

    "encrypt BTI applications" in {

      val c = createCase(app = bti)
      val enc = crypto.encrypt(c)

      val encApp = bti.copy(
        holder = encEori,
        contact = encContacts,
        agent = Some(AgentDetails(encEori, c.application.asInstanceOf[BTIApplication].agent.get.letterOfAuthorisation)),
        confidentialInformation = Some("XYZ")
      )

      enc shouldBe c.copy(application = encApp)
    }

    "encrypt Liability orders" in {

      val c = createCase(app = lo)
      val enc = crypto.encrypt(c)

      val encApp = lo.copy(
        holder = encEori,
        contact = encContacts
      )

      enc shouldBe c.copy(application = encApp)
    }

  }

  "decrypt()" should {

    Mockito.when(simmetricCrypto.decrypt(any[Crypted]())).thenReturn(PlainText("XYZ"))

    "decrypt BTI applications" in {

      val c = createCase(app = bti)
      val dec = crypto.decrypt(c)

      val decApp = bti.copy(
        holder = encEori,
        contact = encContacts,
        agent = Some(AgentDetails(encEori, c.application.asInstanceOf[BTIApplication].agent.get.letterOfAuthorisation)),
        confidentialInformation = Some("XYZ")
      )

      dec shouldBe c.copy(application = decApp)
    }

    "decrypt Liability orders" in {

      val c = createCase(app = lo)
      val dec = crypto.decrypt(c)

      val decApp = lo.copy(
        holder = encEori,
        contact = encContacts
      )

      dec shouldBe c.copy(application = decApp)
    }

  }

}
