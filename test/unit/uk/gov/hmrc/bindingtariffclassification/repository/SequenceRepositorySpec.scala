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

import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import reactivemongo.api.DB
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson._
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatSequence
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global

class SequenceRepositorySpec extends BaseMongoIndexSpec
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with MongoSpecSupport
  with Eventually { self =>

  private val mongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private val repository = createMongoRepo

  private def createMongoRepo: SequenceMongoRepository = {
    new SequenceMongoRepository(mongoDbProvider)
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

  "insert" should {
    val sequence = Sequence("name", 0)

    "insert a new document in the collection" in {
      await(repository.insert(sequence)) shouldBe sequence
      collectionSize shouldBe 1

      await(repository.collection.find(selectorByName("name")).one[Sequence]) shouldBe Some(sequence)
    }

    "fail to insert a duplicate sequence name" in {
      await(repository.insert(sequence)) shouldBe sequence
      collectionSize shouldBe 1

      await(repository.collection.find(selectorByName("name")).one[Sequence]) shouldBe Some(sequence)

      val updated: Sequence = sequence.copy(value = 2)

      intercept[DatabaseException] {
        await(repository.insert(updated))
      }.code shouldBe Some(11000)

      collectionSize shouldBe 1
      await(repository.collection.find(selectorByName("name")).one[Sequence]) shouldBe Some(sequence)
    }
  }

  "get by name" should {

    "return existing sequence" in {
      val sequence = Sequence("name", 10)
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
        Index(key = Seq("name" -> Ascending), name = Some("name_Index"), unique = true),
        Index(key = Seq("_id" -> Ascending), name = Some("_id_"))
      )

      val repo = createMongoRepo
      await(repo.ensureIndexes)

      import scala.concurrent.duration._

      eventually(timeout(5.seconds), interval(100.milliseconds)) {
        assertIndexes(expectedIndexes.sorted, getIndexes(repo.collection).sorted)
      }

      await(repo.drop)
    }
  }

}
