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

package uk.gov.hmrc.bindingtariffclassification.scheduler

import java.time._

import akka.actor.{ActorSystem, Cancellable}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.{never, reset, verify, verifyNoMoreInteractions}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.SchedulerRunEvent
import uk.gov.hmrc.bindingtariffclassification.repository.SchedulerLockRepository
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful
import scala.concurrent.duration._

class SchedulerTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach with Eventually {

  private val zone: ZoneId = ZoneOffset.UTC
  private val schedulerRepository = mock[SchedulerLockRepository]
  private val job = mock[ScheduledJob]
  private val actorSystem = mock[ActorSystem]
  private val internalScheduler = mock[akka.actor.Scheduler]
  private val config = mock[AppConfig]
  private val now: Instant = "2018-12-25T12:00:00"
  private val clock = Clock.fixed(now, zone)
  private val util = mock[SchedulerDateUtil]

  private def instant(datetime: String) = {
    LocalDateTime.parse(datetime).atZone(zone).toInstant
  }

  private implicit def string2Instant: String => Instant = {
    datetime => instant(datetime)
  }

  private implicit def string2Time: String => LocalTime = {
    time => LocalTime.parse(time)
  }

  private implicit def instant2Time: Instant => LocalTime = _.atZone(zone).toLocalTime


  private def givenTheLockSucceeds(): Unit = {
    given(schedulerRepository.lock(any[SchedulerRunEvent])).willReturn(successful(true))
  }

  private def givenTheLockFails(): Unit = {
    given(schedulerRepository.lock(any[SchedulerRunEvent])).willReturn(successful(false))
  }

  private def theLockEvent: SchedulerRunEvent = {
    val captor: ArgumentCaptor[SchedulerRunEvent] = ArgumentCaptor.forClass(classOf[SchedulerRunEvent])
    verify(schedulerRepository).lock(captor.capture())
    captor.getValue
  }

  private def runTheJobImmediately: Answer[Cancellable] = new Answer[Cancellable] {
    override def answer(invocation: InvocationOnMock): Cancellable = {
      val arg: Runnable = invocation.getArgument(2)
      if (arg != null) {
        arg.run()
      }
      Cancellable.alreadyCancelled
    }
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    given(config.clock) willReturn clock
    given(actorSystem.scheduler) willReturn internalScheduler
    given(internalScheduler.schedule(any[FiniteDuration], any[FiniteDuration], any[Runnable])(any[ExecutionContext])) will runTheJobImmediately
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

    "Run job with valid schedule" in {
      // Given
      givenTheLockSucceeds()
      given(job.interval) willReturn 3.seconds
      given(job.firstRunTime) willReturn "12:00"
      given(job.name) willReturn "name"
      given(util.nextRun(job.firstRunTime, job.interval)) willReturn "2018-12-25T12:00:00"
      given(util.closestRun(job.firstRunTime, job.interval)) willReturn "2018-12-25T12:00:00"

      // When
      whenTheSchedulerStarts

      // Then
      val schedule = theSchedule
      schedule.interval shouldBe 3.seconds
      schedule.initialDelay shouldBe 0.seconds

      val lockEvent = theLockEvent
      lockEvent.name shouldBe "name"
      lockEvent.runDate shouldBe instant("2018-12-25T12:00:00")
    }

    "Run job with valid schedule and future run-date" in {
      // Given
      givenTheLockSucceeds()
      given(job.interval) willReturn 3.seconds
      given(job.firstRunTime) willReturn "12:00"
      given(job.name) willReturn "name"
      given(util.nextRun(job.firstRunTime, job.interval)).willReturn("2018-12-25T12:00:20")
      given(util.closestRun(job.firstRunTime, job.interval)) willReturn "2018-12-25T12:00:10"

      // When
      whenTheSchedulerStarts

      // Then
      val schedule = theSchedule
      schedule.interval shouldBe 3.seconds
      schedule.initialDelay shouldBe 20.seconds

      val lockEvent = theLockEvent
      lockEvent.name shouldBe "name"
      lockEvent.runDate shouldBe instant("2018-12-25T12:00:10")
    }

    "Fail to schedule job given an invalid run date" in {
      // Given
      givenTheLockSucceeds()
      given(job.interval) willReturn 3.seconds
      given(job.firstRunTime) willReturn "12:00"
      given(job.name) willReturn "name"
      given(util.nextRun(job.firstRunTime, job.interval)).willReturn("2018-12-25T11:59:59")

      // When
      intercept[IllegalArgumentException] {
        whenTheSchedulerStarts
      }
      verifyNoMoreInteractions(internalScheduler)
    }

    "Not execute the job if the lock fails" in {
      // Given
      givenTheLockFails()
      given(job.interval) willReturn 1.seconds
      given(job.firstRunTime) willReturn now
      given(util.nextRun(job.firstRunTime, job.interval)) willReturn "2018-12-25T12:00:00"

      // When
      whenTheSchedulerStarts

      verify(job, never()).execute()
    }

  }

  private def whenTheSchedulerStarts: Scheduler = {
    new Scheduler(actorSystem, config, schedulerRepository, util, job)
  }

  private case class Schedule(initialDelay: FiniteDuration, interval: FiniteDuration)

}
