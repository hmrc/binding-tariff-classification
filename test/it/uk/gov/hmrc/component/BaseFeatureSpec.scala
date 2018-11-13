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

package uk.gov.hmrc.component

import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.bindingtariffclassification.model.{Case, Event, Sequence}
import uk.gov.hmrc.bindingtariffclassification.repository.{CaseMongoRepository, EventMongoRepository, SequenceMongoRepository}

import scala.concurrent.Await.result
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

abstract class BaseFeatureSpec extends FeatureSpec
  with Matchers with GivenWhenThen with GuiceOneServerPerSuite
  with BeforeAndAfterEach with BeforeAndAfterAll {

  private val timeout = 2.seconds

  private lazy val caseStore: CaseMongoRepository = app.injector.instanceOf[CaseMongoRepository]
  private lazy val eventStore: EventMongoRepository = app.injector.instanceOf[EventMongoRepository]
  private lazy val sequenceStore: SequenceMongoRepository = app.injector.instanceOf[SequenceMongoRepository]

  private def dropStores(): Unit = {
    result(caseStore.drop, timeout)
    result(eventStore.drop, timeout)
    result(sequenceStore.drop, timeout)
  }

  private def ensureStoresIndexes(): Unit = {
    result(caseStore.ensureIndexes, timeout)
    result(eventStore.ensureIndexes, timeout)
    result(sequenceStore.ensureIndexes, timeout)
  }

  override protected def beforeEach(): Unit = {
    dropStores()
    ensureStoresIndexes()
  }

  override protected def afterAll(): Unit = {
    dropStores()
  }

  protected def storeCases(cases: Case*): Seq[Case] = {
    cases.map(c => result(caseStore.insert(c), timeout))
  }

  protected def storeEvents(events: Event*): Seq[Event] = {
    events.map(e => result(eventStore.insert(e), timeout))
  }

  protected def storeSequences(sequences: Sequence*): Seq[Sequence] = {
    sequences.map(s => result(sequenceStore.insert(s), timeout))
  }

  protected def caseStoreSize: Int = {
    result(caseStore.mongoCollection.count(), timeout)
  }

  protected def eventStoreSize: Int = {
    result(eventStore.mongoCollection.count(), timeout)
  }

}
