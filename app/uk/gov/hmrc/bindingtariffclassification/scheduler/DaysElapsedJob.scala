/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.scheduler

import java.time._
import java.util.concurrent.TimeUnit

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.lock.LockRepository

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DaysElapsedJob @Inject()(clock: Clock,
                               caseService: CaseService,
                               override val lockRepository: LockRepository) extends LockedScheduledJob {

  override val releaseLockAfter: Duration = Duration.ofMinutes(30)

  override def name: String = "DaysElapsed"

  override def initialDelay: FiniteDuration = timeUntilMidnight()

  override def interval: FiniteDuration = FiniteDuration(24, TimeUnit.HOURS)

  override def executeInLock(implicit ec: ExecutionContext): Future[Result] = {
    caseService.incrementDaysElapsedIfAppropriate(clock)
      .map(count => Result(s"Incremented the Days Elapsed for [$count] cases."))
  }

  private def timeUntilMidnight(): FiniteDuration = {
    val midnightTonight = LocalDate.now(clock).atStartOfDay().plusDays(1)
    val currentTime = LocalDateTime.now(clock)
    FiniteDuration(Duration.between(currentTime, midnightTonight).getSeconds, TimeUnit.SECONDS)
  }
}