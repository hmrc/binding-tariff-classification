package it.uk.gov.hmrc.component

import java.util.concurrent.TimeUnit

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import it.uk.gov.hmrc.stubs._
import org.scalatest._
import org.scalatestplus.play.OneServerPerSuite
import uk.gov.hmrc.bindingtariffclassification.model.Case
import uk.gov.hmrc.bindingtariffclassification.repository.{CaseMongoRepository, CaseRepository}

import scala.concurrent.Await
import scala.concurrent.Await.result
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

case class MockHost(port: Int) {
  val server = new WireMockServer(WireMockConfiguration.wireMockConfig().port(port))
  val mock = new WireMock("localhost", port)
  val url = s"http://localhost:$port"

  val userAgent = "binding-tariff-classification"
}

abstract class BaseFeatureSpec extends FeatureSpec
  with Matchers with GivenWhenThen with OneServerPerSuite
  with BeforeAndAfterEach with BeforeAndAfterAll {

  override lazy val port = 14680
  val serviceUrl = s"http://localhost:$port"

  val timeout = Duration(5, TimeUnit.SECONDS)
  val mocks = Seq(CaseStub)

  override protected def beforeEach(): Unit = {
    mocks.foreach(m => if (!m.server.isRunning) m.server.start())

    result(mongoRepository.drop, timeout)
    result(mongoRepository.ensureIndexes, timeout)
  }

  def mongoRepository = app.injector.instanceOf[CaseMongoRepository]

  def store(caseModel: Case) = {
    Await.result(mongoRepository.insert(caseModel), timeout)
  }

  override protected def afterEach(): Unit = {
    mocks.foreach(_.mock.resetMappings())
  }

  override protected def afterAll(): Unit = {
    mocks.foreach(_.server.stop())
    result(mongoRepository.drop, timeout)
    result(mongoRepository.ensureIndexes, timeout)
  }
}
