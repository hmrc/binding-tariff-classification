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

import java.time.Duration

import org.joda
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}

import scala.concurrent.{ExecutionContext, Future}

trait LockedScheduledJob extends ScheduledJob {

  def executeInLock(implicit ec: ExecutionContext): Future[this.Result]

  val releaseLockAfter: Duration

  val lockRepository: LockRepository

  private lazy val lockKeeper: LockKeeper = new LockKeeper {
    val repo: LockRepository = lockRepository
    val lockId = s"$name-scheduled-job-lock"
    val forceLockReleaseAfter: joda.time.Duration = joda.time.Duration.millis(releaseLockAfter.toMillis)
  }

  def isRunning: Future[Boolean] = lockKeeper.isLocked

  final def execute(implicit ec: ExecutionContext): Future[Result] =
    lockKeeper.tryLock {
      executeInLock
    } map {
      case Some(Result(msg)) => Result(s"Job with $name run and completed with result $msg")
      case None => Result(s"Job with $name cannot acquire mongo lock, not running")
    }

}
