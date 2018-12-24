package uk.gov.hmrc.bindingtariffclassification.scheduler

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{CountDownLatch, TimeUnit}

import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class LockedScheduledJobTest extends UnitSpec with ScalaFutures with GuiceOneAppPerTest {

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure("mongodb.uri" -> "mongodb://localhost:27017/test-play-schedule")
    .build()

  class SimpleJob(val name: String) extends LockedScheduledJob {

    protected lazy val mongoConnection = new MongoDbConnection {}
    protected implicit lazy val db = mongoConnection.db
    override val releaseLockAfter = Duration.ofSeconds(1000)

    val start = new CountDownLatch(1)

    val lockRepository = new LockRepository()

    def continueExecution() = start.countDown()

    val executionCount = new AtomicInteger(0)

    def executions: Int = executionCount.get()

    override def executeInLock(implicit ec: ExecutionContext): Future[Result] = {
      Future {
        start.await()
        Result(executionCount.incrementAndGet().toString)
      }
    }

    override def initialDelay = FiniteDuration(1, TimeUnit.SECONDS)

    override def interval =  FiniteDuration(1, TimeUnit.SECONDS)
  }

  "LockedScheduledJob" should {

    "let job run in sequence" in {
      val job = new SimpleJob("job1")
      job.continueExecution()
      await(job.execute).message shouldBe "Job with job1 run and completed with result 1"
      await(job.execute).message shouldBe "Job with job1 run and completed with result 2"
    }

    "not allow job to run in parallel" in {
      val job = new SimpleJob("job2")

      val pausedExecution = job.execute
      pausedExecution.isCompleted shouldBe false
      job.isRunning.futureValue shouldBe true
      job.execute.futureValue.message shouldBe "Job with job2 cannot acquire mongo lock, not running"
      job.isRunning.futureValue shouldBe true

      job.continueExecution()
      pausedExecution.futureValue.message shouldBe "Job with job2 run and completed with result 1"
      job.isRunning.futureValue shouldBe false
    }

    "should tolerate exceptions in execution" in {
      val job = new SimpleJob("job3") {
        override def executeInLock(implicit ec: ExecutionContext): Future[Result] = throw new RuntimeException
      }

      Try(job.execute.futureValue)

      job.isRunning.futureValue shouldBe false
    }
  }
}
