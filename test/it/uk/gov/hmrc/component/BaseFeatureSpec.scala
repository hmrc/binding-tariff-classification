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

package it.uk.gov.hmrc.component

import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.bindingtariffclassification.model.Case
import uk.gov.hmrc.bindingtariffclassification.repository.CaseMongoRepository

import scala.concurrent.Await.result
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

abstract class BaseFeatureSpec extends FeatureSpec
  with Matchers with GivenWhenThen with GuiceOneServerPerSuite
  with BeforeAndAfterEach with BeforeAndAfterAll {

  private val timeout = 2.seconds

  protected def repository: CaseMongoRepository = app.injector.instanceOf[CaseMongoRepository]

  override protected def beforeEach(): Unit = {
    result(repository.drop, timeout)
    result(repository.ensureIndexes, timeout)
  }

  override protected def afterAll(): Unit = {
    result(repository.drop, timeout)
  }

  protected def store(cases: Case*): Seq[Case] = {
    cases.map(c => result(repository.insert(c), timeout))
  }
}