/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.service

import org.mockito.Mockito._
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.repository.CaseRepository
import util.CaseData

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// scalastyle:off magic.number
class BtaCardServiceSpec extends BaseSpec {

  private val caseRepository = mock[CaseRepository]
  private val service        = new BtaCardService(caseRepository)

  val eori = "123GB"

  "BTA Card Service" should {

    "generate counts for the BTA card" when {

      "supplied with applications and rulings" in {

        when(caseRepository.getAllByEori(eori))
          .thenReturn(
            Future.successful(
              CaseData.createBtaCardData(
                eori                   = eori,
                totalApplications      = 4,
                actionableApplications = 2,
                totalRulings           = 8,
                expiringRulings        = 6
              )
            )
          )

        whenReady(service.generateBtaCard(eori)) { res =>
          res.eori                        shouldBe eori
          res.applications.get.total      shouldBe 4
          res.applications.get.actionable shouldBe 2
          res.rulings.get.total           shouldBe 8
          res.rulings.get.expiring        shouldBe 6
        }
      }

      "supplied with applications and rulings with the same counts of actionable/expiring" in {

        when(caseRepository.getAllByEori(eori))
          .thenReturn(
            Future.successful(
              CaseData.createBtaCardData(
                eori                   = eori,
                totalApplications      = 5,
                actionableApplications = 5,
                totalRulings           = 1,
                expiringRulings        = 1
              )
            )
          )

        whenReady(service.generateBtaCard(eori)) { res =>
          res.eori                        shouldBe eori
          res.applications.get.total      shouldBe 5
          res.applications.get.actionable shouldBe 5
          res.rulings.get.total           shouldBe 1
          res.rulings.get.expiring        shouldBe 1
        }
      }

      "supplied with applications and rulings with no actionables" in {

        when(caseRepository.getAllByEori(eori))
          .thenReturn(
            Future.successful(
              CaseData.createBtaCardData(
                eori                   = eori,
                totalApplications      = 1,
                actionableApplications = 0,
                totalRulings           = 1,
                expiringRulings        = 1
              )
            )
          )

        whenReady(service.generateBtaCard(eori)) { res =>
          res.eori                        shouldBe eori
          res.applications.get.total      shouldBe 1
          res.applications.get.actionable shouldBe 0
          res.rulings.get.total           shouldBe 1
          res.rulings.get.expiring        shouldBe 1
        }
      }

      "supplied with applications and rulings with no expiring" in {

        when(caseRepository.getAllByEori(eori))
          .thenReturn(
            Future.successful(
              CaseData.createBtaCardData(
                eori                   = eori,
                totalApplications      = 1,
                actionableApplications = 0,
                totalRulings           = 1,
                expiringRulings        = 0
              )
            )
          )

        whenReady(service.generateBtaCard(eori)) { res =>
          res.eori                        shouldBe eori
          res.applications.get.total      shouldBe 1
          res.applications.get.actionable shouldBe 0
          res.rulings.get.total           shouldBe 1
          res.rulings.get.expiring        shouldBe 0
        }
      }
    }

    "supplied with applications and no rulings" in {

      when(caseRepository.getAllByEori(eori))
        .thenReturn(
          Future.successful(
            CaseData.createBtaCardData(
              eori                   = eori,
              totalApplications      = 5,
              actionableApplications = 5,
              totalRulings           = 0,
              expiringRulings        = 0
            )
          )
        )

      whenReady(service.generateBtaCard(eori)) { res =>
        res.eori                        shouldBe eori
        res.applications.get.total      shouldBe 5
        res.applications.get.actionable shouldBe 5
        res.rulings                     shouldBe None
      }
    }

    "supplied with rulings and no applications" in {

      when(caseRepository.getAllByEori(eori))
        .thenReturn(
          Future.successful(
            CaseData.createBtaCardData(
              eori                   = eori,
              totalApplications      = 0,
              actionableApplications = 0,
              totalRulings           = 1,
              expiringRulings        = 1
            )
          )
        )

      whenReady(service.generateBtaCard(eori)) { res =>
        res.eori                 shouldBe eori
        res.applications         shouldBe None
        res.rulings.get.total    shouldBe 1
        res.rulings.get.expiring shouldBe 1
      }
    }

    "supplied with no rulings and no applications" in {

      when(caseRepository.getAllByEori(eori))
        .thenReturn(
          Future.successful(
            CaseData.createBtaCardData(
              eori                   = eori,
              totalApplications      = 0,
              actionableApplications = 0,
              totalRulings           = 0,
              expiringRulings        = 0
            )
          )
        )

      whenReady(service.generateBtaCard(eori)) { res =>
        res.eori         shouldBe eori
        res.applications shouldBe None
        res.rulings      shouldBe None
      }
    }

    "supplied with rulings that expire within 3 months" in {

      when(caseRepository.getAllByEori(eori))
        .thenReturn(
          Future.successful(
            CaseData.createBtaCardData(
              eori                   = eori,
              totalApplications      = 0,
              actionableApplications = 0,
              totalRulings           = 3,
              expiringRulings        = 1,
              expiryMonths           = Some(3)
            )
          )
        )

      whenReady(service.generateBtaCard(eori)) { res =>
        res.eori                 shouldBe eori
        res.applications         shouldBe None
        res.rulings.get.total    shouldBe 3
        res.rulings.get.expiring shouldBe 1
      }
    }

    "supplied with rulings that have an expiry date of 3 months and 1 day" in {

      when(caseRepository.getAllByEori(eori))
        .thenReturn(
          Future.successful(
            CaseData.createBtaCardData(
              eori                   = eori,
              totalApplications      = 0,
              actionableApplications = 0,
              totalRulings           = 3,
              expiringRulings        = 1,
              expiryMonths           = Some(3),
              expiryDays             = Some(1)
            )
          )
        )

      whenReady(service.generateBtaCard(eori)) { res =>
        res.eori                 shouldBe eori
        res.applications         shouldBe None
        res.rulings.get.total    shouldBe 3
        res.rulings.get.expiring shouldBe 0
      }
    }
  }
}
