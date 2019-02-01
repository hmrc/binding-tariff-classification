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

import javax.inject._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.search.{Filter, Search}
import uk.gov.hmrc.crypto.{CompositeSymmetricCrypto, Crypted, PlainText}

@Singleton
class Crypto @Inject()(crypto: CompositeSymmetricCrypto) {

  def encrypt(c: Case): Case = {
    applyCrypto(c) { s: String => crypto.encrypt(PlainText(s)).value }
  }

  def encrypt(search: Search): Search = {
    applyCrypto(search) { s: String => crypto.encrypt(PlainText(s)).value }
  }

  def decrypt(c: Case): Case = {
    applyCrypto(c) { s: String => crypto.decrypt(Crypted(s)).value }
  }

  private def applyCrypto(filter: Filter)(f: String => String): Filter = {
    filter.copy(traderName = filter.traderName map f)
  }

  private def applyCrypto(search: Search)(f: String => String): Search = {
    search.copy(filter = applyCrypto(search.filter)(f))
  }

  private def applyCrypto(c: Contact)(f: String => String): Contact = {
    c.copy(
      name = f(c.name),
      email = f(c.email),
      phone = c.phone map f
    )
  }

  private def applyCrypto(e: EORIDetails)(f: String => String): EORIDetails = {
    e.copy(
      eori = f(e.eori),
      businessName = f(e.businessName),
      addressLine1 = f(e.addressLine1),
      addressLine2 = f(e.addressLine2),
      addressLine3 = f(e.addressLine3),
      postcode = f(e.postcode),
      country = f(e.country)
    )
  }

  private def applyCrypto(a: AgentDetails)(f: String => String): AgentDetails = {
    a.copy(eoriDetails = applyCrypto(a.eoriDetails)(f))
  }

  private def applyCrypto(c: Case)(f: String => String): Case = {

    import ApplicationType._

    c.application.`type` match {
      case BTI =>
        val bti = c.application.asInstanceOf[BTIApplication]
        c.copy(
          application = bti.copy(
            holder = applyCrypto(bti.holder)(f),
            contact = applyCrypto(bti.contact)(f),
            agent = bti.agent map ( applyCrypto(_)(f) ),
            confidentialInformation = bti.confidentialInformation map f
          )
        )
      case LIABILITY_ORDER =>
        val l = c.application.asInstanceOf[LiabilityOrder]
        c.copy(
          application = l.copy(
            holder = applyCrypto(l.holder)(f),
            contact = applyCrypto(l.contact)(f)
          )
        )
      case t: ApplicationType =>
        throw new IllegalStateException(s"Unexpected application type: $t")
    }

  }

}
