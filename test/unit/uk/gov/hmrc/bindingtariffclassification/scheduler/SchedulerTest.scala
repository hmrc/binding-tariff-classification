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

import java.time.{Clock, Instant, LocalDateTime, ZoneId}
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Cancellable}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.{never, reset, verify}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.SchedulerRunEvent
import uk.gov.hmrc.bindingtariffclassification.repository.SchedulerLockRepository
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class SchedulerTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach with BeforeAndAfterAll {

  private val zone = ZoneId.of("UTC")
  private val schedulerRepository = mock[SchedulerLockRepository]
  private val job = mock[ScheduledJob]
  private val actorSystem = mock[ActorSystem]
  private val internalScheduler = mock[akka.actor.Scheduler]
  private val config = mock[AppConfig]
  private val now: Instant = instantOf("2018-12-25T12:00:00")
  private val clock = Clock.fixed(now, zone)

  private def instantOf(datetime: String): Instant = {
    LocalDateTime.parse(datetime).atZone(zone).toInstant
  }

  private def givenTheLockSucceeds(): Unit = {
    given(schedulerRepository.lock(any[SchedulerRunEvent])).willReturn(Future.successful(true))
  }

  private def givenTheLockFails(): Unit = {
    given(schedulerRepository.lock(any[SchedulerRunEvent])).willReturn(Future.successful(false))
  }

  private def theLockEvent: SchedulerRunEvent = {
    val captor: ArgumentCaptor[SchedulerRunEvent] = ArgumentCaptor.forClass(classOf[SchedulerRunEvent])
    verify(schedulerRepository).lock(captor.capture())
    captor.getValue
  }

  private def runTheJobImmediately: Answer[Cancellable] = new Answer[Cancellable]{
    override def answer(invocation: InvocationOnMock): Cancellable = {
      val arg: Runnable = invocation.getArgument(2)
      if(arg != null) {
        arg.run()
      }
      Cancellable.alreadyCancelled
    }
  }

  override protected def beforeEach(): Unit = {
    given(config.clock).willReturn(clock)
    given(actorSystem.scheduler).willReturn(internalScheduler)
    given(internalScheduler.schedule(any[FiniteDuration], any[FiniteDuration], any[Runnable])(any[ExecutionContext])).will(runTheJobImmediately)
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    reset(schedulerRepository, job, internalScheduler)
  }

  private def theSchedule: Schedule = {
    val intervalCaptor: ArgumentCaptor[FiniteDuration] = ArgumentCaptor.forClass(classOf[FiniteDuration])
    val initialDelayCaptor: ArgumentCaptor[FiniteDuration] = ArgumentCaptor.forClass(classOf[FiniteDuration])
    verify(internalScheduler).schedule(initialDelayCaptor.capture(), intervalCaptor.capture(), any[Runnable])(any[ExecutionContext])
    Schedule(initialDelayCaptor.getValue, intervalCaptor.getValue)
  }

  "Scheduler" should {

    "Run job with starting now" in {
      // Given
      givenTheLockSucceeds()
      given(job.interval) willReturn FiniteDuration(1, TimeUnit.SECONDS)
      given(job.firstRunDate) willReturn now

      // When
      new Scheduler(actorSystem, config, schedulerRepository, job)

      // Then
      val schedule = theSchedule
      schedule.interval shouldBe FiniteDuration(1, TimeUnit.SECONDS)
      schedule.initialDelay shouldBe FiniteDuration(0, TimeUnit.SECONDS)
    }

    "Run job with starting in the future" in {
      // Given
      givenTheLockSucceeds()
      given(job.interval) willReturn FiniteDuration(1, TimeUnit.SECONDS)
      given(job.firstRunDate) willReturn now.plusSeconds(1)

      // When
      new Scheduler(actorSystem, config, schedulerRepository, job)

      // Then
      val schedule = theSchedule
      schedule.interval shouldBe FiniteDuration(1, TimeUnit.SECONDS)
      schedule.initialDelay shouldBe FiniteDuration(1, TimeUnit.SECONDS)
    }

    "Run job with starting in the past" in {
      // Given
      givenTheLockSucceeds()
      given(job.interval) willReturn FiniteDuration(1, TimeUnit.SECONDS)
      given(job.firstRunDate) willReturn now.minusSeconds(1)

      // When
      new Scheduler(actorSystem, config, schedulerRepository, job)

      // Then
      val schedule = theSchedule
      schedule.interval shouldBe FiniteDuration(1, TimeUnit.SECONDS)
      schedule.initialDelay shouldBe FiniteDuration(0, TimeUnit.SECONDS)
    }

    "Create the lock event with the intended run time" in {
      // Given
      givenTheLockSucceeds()
      given(job.interval) willReturn FiniteDuration(1, TimeUnit.SECONDS)
      given(job.firstRunDate) willReturn now.plusSeconds(1)
      given(job.name) willReturn "name"

      // When
      new Scheduler(actorSystem, config, schedulerRepository, job)

      // Then
      val event = theLockEvent
      event.name shouldBe "name"
      event.runDate shouldBe now.plusSeconds(1)
    }

    "Not execute the job if the lock fails" in {
      // Given
      givenTheLockFails()
      given(job.interval) willReturn FiniteDuration(1, TimeUnit.SECONDS)
      given(job.firstRunDate) willReturn now

      // When
      new Scheduler(actorSystem, config, schedulerRepository, job)

      verify(job, never()).execute()
    }
  }

  private case class Schedule(initialDelay: FiniteDuration, interval: FiniteDuration)

}
