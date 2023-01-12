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

import javax.inject._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.crypto.{CompositeSymmetricCrypto, Crypted, PlainText}

@Singleton
class Crypto @Inject() (crypto: CompositeSymmetricCrypto) {

  def encrypt(c: Case): Case =
    applyCrypto(c)(encryptString)

  def decrypt(c: Case): Case =
    applyCrypto(c)(decryptString)

  def encryptString: String => String = { s: String => crypto.encrypt(PlainText(s)).value }

  private def decryptString: String => String = { s: String => crypto.decrypt(Crypted(s)).value }

  private def applyCrypto(c: Contact)(f: String => String): Contact =
    c.copy(
      name  = f(c.name),
      email = f(c.email),
      phone = c.phone map f
    )

  private def applyCrypto(e: EORIDetails)(f: String => String): EORIDetails =
    e.copy(
      eori         = f(e.eori),
      businessName = e.businessName,
      addressLine1 = f(e.addressLine1),
      addressLine2 = f(e.addressLine2),
      addressLine3 = f(e.addressLine3),
      postcode     = f(e.postcode),
      country      = f(e.country)
    )

  private def applyCrypto(a: AgentDetails)(f: String => String): AgentDetails =
    a.copy(eoriDetails = applyCrypto(a.eoriDetails)(f))

  private def applyCrypto(a: Address)(f: String => String): Address =
    a.copy(
      buildingAndStreet = f(a.buildingAndStreet),
      townOrCity        = f(a.townOrCity),
      county            = a.county.map(f(_)),
      postCode          = a.postCode.map(f(_))
    )

  private def applyCrypto(c: Case)(f: String => String): Case = {

    import ApplicationType._

    c.application.`type` match {
      case BTI =>
        val bti = c.application.asBTI
        c.copy(
          application = bti.copy(
            holder                  = applyCrypto(bti.holder)(f),
            contact                 = applyCrypto(bti.contact)(f),
            agent                   = bti.agent map (applyCrypto(_)(f)),
            confidentialInformation = bti.confidentialInformation map f
          )
        )
      case LIABILITY_ORDER =>
        val l = c.application.asLiabilityOrder
        c.copy(
          application = l.copy(
            contact = applyCrypto(l.contact)(f)
          )
        )
      case MISCELLANEOUS =>
        val misc = c.application.asMisc
        c.copy(
          application = misc.copy(
            contact     = applyCrypto(misc.contact)(f),
            contactName = misc.contactName.map(f(_)),
            name        = f(misc.name)
          )
        )
      case CORRESPONDENCE =>
        val corres = c.application.asCorrespondence
        c.copy(
          application = corres.copy(
            contact   = applyCrypto(corres.contact)(f),
            agentName = corres.agentName.map(f(_)),
            address   = applyCrypto(corres.address)(f)
          )
        )
      case t: ApplicationType =>
        throw new IllegalStateException(s"Unexpected application type: $t")
    }

  }

}
