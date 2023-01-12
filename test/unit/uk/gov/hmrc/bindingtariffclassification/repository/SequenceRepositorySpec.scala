/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions}
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.mongo.test.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class SequenceRepositorySpec
    extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with MongoSupport
    with Eventually { self =>

  private val repository = createMongoRepo

  private def createMongoRepo: SequenceMongoRepository =
    new SequenceMongoRepository(mongoComponent)

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.collection.deleteMany(Filters.empty()).toFuture())
    collectionSize shouldBe 0
  }

  override def afterAll(): Unit = {
    super.afterAll()
    await(repository.collection.deleteMany(Filters.empty()).toFuture())
  }

  private def collectionSize: Int =
    await(
      repository.collection
        .countDocuments()
        .toFuture()
        .map(_.toInt)
    )

  private def selectorByName() =
    Filters.equal("name", "name")

  "insert" should {
    val sequence = Sequence("name", 0)

    "insert a new document in the collection" in {
      await(repository.insert(sequence)) shouldBe sequence
      collectionSize                     shouldBe 1

      await(repository.collection.find(selectorByName()).headOption()) shouldBe Some(sequence)
    }

    "fail to insert a duplicate sequence name" in {
      await(repository.insert(sequence)) shouldBe sequence
      collectionSize                     shouldBe 1

      await(repository.collection.find(selectorByName()).headOption()) shouldBe Some(sequence)

      val updated: Sequence = sequence.copy(value = 2)
      val errorCode: Int    = 11000

      intercept[MongoWriteException] {
        await(repository.insert(updated))
      }.getError.getCode shouldBe errorCode

      collectionSize                                                   shouldBe 1
      await(repository.collection.find(selectorByName()).headOption()) shouldBe Some(sequence)
    }
  }

  "get by name" should {

    "return existing sequence" in {
      val value: Int = 10
      val sequence   = Sequence("name", value)
      await(repository.insert(sequence))
      await(repository.getByName("name")) shouldBe sequence
    }

    "initialize if not found" in {
      await(repository.getByName("name")) shouldBe Sequence("name", 1)
    }
  }

  "increment and get by name" should {

    "return existing sequence" in {
      await(repository.insert(Sequence("name", 0)))
      await(repository.incrementAndGetByName("name")) shouldBe Sequence("name", 1)
      await(repository.incrementAndGetByName("name")) shouldBe Sequence("name", 2)
    }

    "initialize if not found" in {
      await(repository.getByName("name")) shouldBe Sequence("name", 1)
    }
  }

  "The 'sequences' collection" should {

    "have all expected indexes" in {

      val expectedIndexes = List(
        IndexModel(ascending("name"), IndexOptions().name("name_Index").unique(true)),
        IndexModel(ascending("_id"), IndexOptions().name("_id_"))
      )

      val repo = createMongoRepo
      await(repo.ensureIndexes)

      eventually(timeout(5.seconds), interval(100.milliseconds)) {
        assertIndexes(expectedIndexes.sorted, getIndexes(repo.collection).sorted)
      }

      await(repo.collection.drop())
    }
  }

}
