/*
 * Copyright 2025 HM Revenue & Customs
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

import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions}
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.mongo.test.MongoSupport

import java.time.{LocalDate, ZoneOffset, ZonedDateTime}
import scala.concurrent.ExecutionContext.Implicits.global

class MigrationLockRepositorySpec
    extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with MongoSupport
    with Eventually { self =>

  private val repository = newMongoRepo

  private def newMongoRepo: MigrationLockMongoRepository =
    new MigrationLockMongoRepository(mongoComponent)

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.deleteAll())
    await(repository.ensureIndexes)
    collectionSize shouldBe 0
  }

  override def afterAll(): Unit = {
    super.afterAll()
    await(repository.deleteAll())
  }

  private def collectionSize: Int =
    await(
      repository.collection
        .countDocuments()
        .toFuture()
        .map(_.toInt)
    )

  private def selectorByName() = Filters.equal("name", "name")

  private def date(date: String): ZonedDateTime =
    LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC)

  private val event = JobRunEvent("name", date("2018-12-25"))

  "lock" should {
    "insert a new document in the collection" in {
      await(repository.lock(event)) shouldBe true
      collectionSize                shouldBe 1

      await(repository.collection.find(selectorByName()).headOption()) shouldBe Some(event)
    }

    "fail to insert a duplicate event name" in {
      await(repository.lock(event)) shouldBe true
      collectionSize                shouldBe 1

      await(repository.lock(event.copy(runDate = date("2018-12-26")))) shouldBe false
      collectionSize                                                   shouldBe 1
    }
  }

  "delete" should {
    "remove the document from the collection" in {
      await(repository.lock(event)) shouldBe true
      collectionSize                shouldBe 1

      await(repository.delete(event))
      collectionSize shouldBe 0
    }
  }

  "deleteAll" should {
    "remove all documents from the collection" in {
      val event2 = JobRunEvent("name2", date("2018-12-26"))
      await(repository.lock(event))  shouldBe true
      await(repository.lock(event2)) shouldBe true
      collectionSize                 shouldBe 2

      await(repository.deleteAll())
      collectionSize shouldBe 0
    }
  }

  "The 'scheduler' collection" should {

    "have all expected indexes" in {

      val expectedIndexes = List(
        IndexModel(ascending("name"), IndexOptions().name("name_Index").unique(true)),
        IndexModel(ascending("_id"), IndexOptions().name("_id_"))
      )

      val repo = newMongoRepo
      await(repo.ensureIndexes())

      import scala.concurrent.duration._

      eventually(timeout(5.seconds), interval(100.milliseconds)) {
        assertIndexes(expectedIndexes.sorted, getIndexes(repo.collection).sorted)
      }

      await(repo.collection.drop())
    }
  }

  "findOne" should {

    "return no elements when the collection is empty" in {
      await(repository.findOne("name")) shouldBe None
    }

    "return the element when the collection has one" in {
      await(repository.lock(event))
      await(repository.findOne("name")) shouldBe Some(event)
    }

  }

}
