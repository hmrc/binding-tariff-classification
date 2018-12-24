package uk.gov.hmrc.bindingtariffclassification.scheduler

import java.time._
import java.util.concurrent.TimeUnit.{HOURS, SECONDS}

import org.mockito.BDDMockito.given
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.service.CaseService
import uk.gov.hmrc.lock.LockRepository
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class DaysElapsedJobTest extends UnitSpec with MockitoSugar {

  private val zone = ZoneId.of("UTC")
  private val elevenPM = LocalTime.of(23, 0).atDate(LocalDate.now()).atZone(zone).toInstant
  private val clock = Clock.fixed(elevenPM, zone)
  private val caseService = mock[CaseService]
  private val lockRepository = mock[LockRepository]
  private val job = new DaysElapsedJob(clock, caseService, lockRepository)

  "Scheduled Job" should {
    "Configure 'Name'" in {
      job.name shouldBe "DaysElapsed"
    }

    "Configure 'initialDelay" in {
      job.initialDelay shouldBe FiniteDuration(60*60, SECONDS)
    }

    "Configure 'interval'" in {
      job.interval shouldBe FiniteDuration(24, HOURS)
    }

    "Execute in lock" in {
      given(caseService.incrementDaysElapsedIfAppropriate(clock)).willReturn(Future.successful(2))

      await(job.executeInLock).message shouldBe "Incremented the Days Elapsed for [2] cases."
    }

  }

}
