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

import uk.gov.hmrc.bindingtariffclassification.common.Logging
import uk.gov.hmrc.bindingtariffclassification.model.Case
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus._
import uk.gov.hmrc.bindingtariffclassification.model.bta.{BtaApplications, BtaCard, BtaRulings}
import uk.gov.hmrc.bindingtariffclassification.repository.CaseRepository

import java.time.{LocalDateTime, ZoneOffset}
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BtaCardService @Inject()(caseRepository: CaseRepository)(implicit ec: ExecutionContext) extends Logging {

  private final lazy val applicationStatuses = List(NEW, OPEN, REFERRED)
  private final lazy val ignoredStatuses = List(DRAFT, REJECTED, CANCELLED, ANNULLED, REVOKED)
  private final lazy val rulingExpiryInMonths = 3L
  private case class RulingTotals(total: Int, expiring: Int)

  def generateBtaCard(eori: String): Future[BtaCard] = caseRepository.getAllByEori(eori).map { cases =>
    logger.info(s"[BtaCardService][generateBtaCard] Found records linked to EORI: ${cases.size}")
    val data = cases.filterNot(ignoredStatuses.contains).groupBy(_.status)
    val totalRulings = calculateRulingTotals(data.getOrElse(COMPLETED, List.empty))
    val totalApplications = data.filter { case (caseStatus, _) => applicationStatuses.contains(caseStatus) }
    BtaCard(
      eori = eori,
      applications = if (totalApplications.isEmpty) None else {
        Some(BtaApplications(
          total = totalApplications.map { case (_, applications) => applications.length }.sum,
          actionable = totalApplications.getOrElse(REFERRED, List.empty).length))
      },
      rulings = if (totalRulings.total == 0) None else {
        Some(BtaRulings(
          total = totalRulings.total,
          expiring = totalRulings.expiring
        ))
      }
    )
  }

  private def calculateRulingTotals(rulings: List[Case]): RulingTotals = {
    val now = LocalDateTime.now().atOffset(ZoneOffset.UTC).toInstant
    val activeRulings = rulings.filter(_.decision.flatMap(_.effectiveEndDate.map(_.isAfter(now))).getOrElse(false))
    val expiringRulings = activeRulings.count(_.decision.get.effectiveEndDate.get.isBefore(now.atOffset(ZoneOffset.UTC)
      .plusMonths(rulingExpiryInMonths).toInstant))
    RulingTotals(activeRulings.length, expiringRulings)
  }
}
