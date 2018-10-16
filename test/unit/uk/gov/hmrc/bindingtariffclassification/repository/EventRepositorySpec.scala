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

package unit.uk.gov.hmrc.bindingtariffclassification.repository

import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import reactivemongo.api.DB
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson._
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtariffclassification.model.JsonFormatters.formatEvent
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.repository.{BaseMongoIndexSpec, EventMongoRepository, MongoDbProvider}
import uk.gov.hmrc.bindingtariffclassification.todelete.EventData._
import uk.gov.hmrc.bindingtariffclassification.utils.RandomGenerator
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global

class EventRepositorySpec extends BaseMongoIndexSpec
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with MongoSpecSupport
  with Eventually { self =>

  private val mongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private def getIndexes(repo: EventMongoRepository): List[Index] = {
    val indexesFuture = repo.collection.indexesManager.list()
    await(indexesFuture)
  }

  private val repository = new EventMongoRepository(mongoDbProvider)

  private val e: Event = createNoteEvent("")

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    await(repository.drop)
  }

  private def collectionSize: Int = {
    await(repository.collection.count())
  }

  "insert" should {

    "insert a new document in the collection" in {
      collectionSize shouldBe 0

      await(repository.insert(e)) shouldBe e
      collectionSize shouldBe 1

      await(repository.collection.find(selectorById(e)).one[Event]) shouldBe Some(e)
    }

    "fail to insert an existing document in the collection" in {
      collectionSize shouldBe 0

      await(repository.insert(e)) shouldBe e
      collectionSize shouldBe 1

      await(repository.collection.find(selectorById(e)).one[Event]) shouldBe Some(e)

      val updated: Event = e.copy(userId = "user_A")

      val caught = intercept[DatabaseException] {
        await(repository.insert(updated))
      }
      caught.code shouldBe Some(11000)

      collectionSize shouldBe 1
      await(repository.collection.find(selectorById(updated)).one[Event]) shouldBe Some(e)
    }
  }

  "getByCaseReference" should {

    "retrieve all expected events from the collection" in {
      val e1 = createNoteEvent("REF_1")
      val e2 = createCaseStatusChangeEvent("REF_2")

      await(repository.insert(e1))
      await(repository.insert(e2))
      collectionSize shouldBe 2

      await(repository.getByCaseReference("REF_1")) shouldBe Seq(e1)
    }

    "return an empty sequence when there are no events matching the case reference" in {
      await(repository.insert(createCaseStatusChangeEvent("REF_1")))
      collectionSize shouldBe 1

      await(repository.getByCaseReference("REF_2")) shouldBe Seq.empty
    }
  }

  "getById" should {

    "retrieve the correct record" in {
      await(repository.insert(e))
      collectionSize shouldBe 1

      await(repository.getById(e.id)) shouldBe Some(e)
    }

    "return 'None' when the 'id' doesn't match any record in the collection" in {
      for (_ <- 1 to 3) {
        await(repository.insert(createNoteEvent("")))
      }
      collectionSize shouldBe 3

      await(repository.getById("WRONG_ID")) shouldBe None
    }
  }

  "The 'events' collection" should {

    "have a unique index based on the field 'id' " in {
      val eventId = RandomGenerator.randomUUID()
      val e1 = e.copy(id = eventId)
      await(repository.insert(e1))
      collectionSize shouldBe 1

      val caught = intercept[DatabaseException] {
        val e2 = e1.copy(caseReference = "DEF", userId = "user_123")
        await(repository.insert(e2))
      }

      caught.code shouldBe Some(11000)

      collectionSize shouldBe 1
    }

    "have all expected indexes" in {

      import scala.concurrent.duration._

      val expectedIndexes = List(
        Index(key = Seq("id" -> Ascending), name = Some("id_Index"), unique = true, background = true),
        Index(key = Seq("caseReference" -> Ascending), name = Some("caseReference_Index"), unique = false, background = true),
        Index(key = Seq("_id" -> Ascending), name = Some("_id_"))
      )

      val repo = new EventMongoRepository(mongoDbProvider)

      eventually(timeout(5.seconds), interval(100.milliseconds)) {
        assertIndex(expectedIndexes.sorted, getIndexes(repo).sorted)
      }
    }
  }

  private def selectorById(e: Event) = {
    BSONDocument("id" -> e.id)
  }

}
