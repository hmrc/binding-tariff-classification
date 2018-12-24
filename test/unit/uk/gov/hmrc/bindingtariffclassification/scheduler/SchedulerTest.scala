package uk.gov.hmrc.bindingtariffclassification.scheduler

import java.util.concurrent.{CountDownLatch, TimeUnit}

import akka.actor.ActorSystem
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class SchedulerTest extends UnitSpec {

  "Scheduler" should {
    "Run job with interval" in {
      // Given
      val latch = new CountDownLatch(2)
      val job = new ExampleJob(latch, intervalSeconds = 1)

      new Scheduler(ActorSystem("Test"), job)

      if(!latch.await(2, TimeUnit.SECONDS)) {
        fail("Job did not run in the time frame")
      }
    }

    "Run job with initial delay" in {
      // Given
      val latch = new CountDownLatch(1)
      val job = new ExampleJob(latch, initialDelaySeconds = 2)

      new Scheduler(ActorSystem("Test"), job)

      if(latch.await(1, TimeUnit.SECONDS)) {
        fail("Job ran when it was expected to be delayed")
      }
    }
  }

  class ExampleJob(latch: CountDownLatch, intervalSeconds: Int = 0, initialDelaySeconds: Int = 0) extends ScheduledJob {
    override def name: String = "Name"

    override def execute(implicit ec: ExecutionContext): Future[Result] = {
      latch.countDown()
      Future(Result("Success"))
    }

    override def isRunning: Future[Boolean] = ???

    override def initialDelay: FiniteDuration = FiniteDuration(initialDelaySeconds, TimeUnit.SECONDS)

    override def interval: FiniteDuration = FiniteDuration(intervalSeconds, TimeUnit.SECONDS)
  }

}
