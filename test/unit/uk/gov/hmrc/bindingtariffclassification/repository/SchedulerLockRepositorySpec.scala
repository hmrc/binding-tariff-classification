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

package uk.gov.hmrc.bindingtariffclassification.repository

import java.time.{Instant, LocalDate, ZoneId}

import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import reactivemongo.api.DB
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson._
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatSchedulerRunEvent
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global

class SchedulerLockRepositorySpec extends BaseMongoIndexSpec
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with MongoSpecSupport
  with Eventually { self =>

  private val mongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private val repository = newMongoRepo

  private def newMongoRepo: SchedulerLockMongoRepository = {
    new SchedulerLockMongoRepository(mongoDbProvider)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.drop)
    await(repository.ensureIndexes)
    collectionSize shouldBe 0
  }

  override def afterAll(): Unit = {
    super.afterAll()
    await(repository.drop)
  }

  private def collectionSize: Int = {
    await(repository.collection.count())
  }

  private def selectorByName(name: String): BSONDocument = {
    BSONDocument("name" -> name)
  }

  private def date(date: String): Instant = {
    LocalDate.parse(date).atStartOfDay(ZoneId.of("UTC")).toInstant
  }

  "lock" should {
    val event = SchedulerRunEvent("name", date("2018-12-25"))

    "insert a new document in the collection" in {
      await(repository.lock(event)) shouldBe true
      collectionSize shouldBe 1

      await(repository.collection.find(selectorByName("name")).one[SchedulerRunEvent]) shouldBe Some(event)
    }

    "insert a multiple documents in the collection with different runDates" in {
      await(repository.lock(event)) shouldBe true
      val event2 = SchedulerRunEvent("name", date("2018-12-26"))
      await(repository.lock(event2)) shouldBe true
      collectionSize shouldBe 2
    }

    "fail to insert a duplicate event name & runDate" in {
      await(repository.lock(event)) shouldBe true
      collectionSize shouldBe 1

      await(repository.lock(event)) shouldBe false
      collectionSize shouldBe 1
    }
  }

  "The 'scheduler' collection" should {

    "have all expected indexes" in {

      val expectedIndexes = List(
        Index(key = Seq("name" -> Ascending, "runDate" -> Ascending), name = Some("name_runDate_Index"), unique = true),
        Index(key = Seq("_id" -> Ascending), name = Some("_id_"))
      )

      val repo = newMongoRepo
      await(repo.ensureIndexes)

      import scala.concurrent.duration._

      eventually(timeout(5.seconds), interval(100.milliseconds)) {
        assertIndexes(expectedIndexes.sorted, getIndexes(repo.collection).sorted)
      }

      await(repo.drop)
    }
  }

}
