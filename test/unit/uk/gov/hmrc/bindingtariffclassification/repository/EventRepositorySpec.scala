/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.test.Helpers. _
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.api.{DB, ReadConcern}
import reactivemongo.bson._
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatEvent
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.utils.RandomGenerator
import uk.gov.hmrc.mongo.MongoSpecSupport
import util.EventData._

import java.time.{Instant, LocalDate, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global

// scalastyle:off magic.number
class EventRepositorySpec
    extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with MongoSpecSupport
    with Eventually {
  self =>
  private val mongoErrorCode = 11000

  private val mongoDbProvider: MongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private val repository = createMongoRepo

  private def createMongoRepo =
    new EventMongoRepository(mongoDbProvider)

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

  private def collectionSize: Int =
    await(
      repository.collection
        .count(selector = None, limit = Some(0), skip = 0, hint = None, readConcern = ReadConcern.Local)
    ).toInt

  "deleteAll()" should {

    "clear the collection" in {
      val size = collectionSize

      await(repository.insert(e))
      collectionSize shouldBe 1 + size

      await(repository.deleteAll()) shouldBe ((): Unit)
      collectionSize                shouldBe size
    }

  }

  "delete" should {
    "clear events by case reference" in {
      val e1 = createNoteEvent("REF_1")
      val e2 = createCaseStatusChangeEvent("REF_1")
      val e3 = createCaseStatusChangeEvent("REF_2")

      await(repository.insert(e1))
      await(repository.insert(e2))
      await(repository.insert(e3))
      collectionSize shouldBe 3

      await(repository.delete(EventSearch(caseReference = Some(Set("REF_1")))))
      collectionSize                                                 shouldBe 1
      await(repository.collection.find(selectorById(e1)).one[Event]) shouldBe None
      await(repository.collection.find(selectorById(e2)).one[Event]) shouldBe None
      await(repository.collection.find(selectorById(e3)).one[Event]) shouldBe Some(e3)
    }

    "clear events by event type" in {
      val e1 = createNoteEvent("REF_1")
      val e2 = createCaseStatusChangeEvent("REF_2")

      await(repository.insert(e1))
      await(repository.insert(e2))
      collectionSize shouldBe 2

      await(repository.delete(EventSearch(`type` = Some(Set(EventType.NOTE)))))
      collectionSize                                                 shouldBe 1
      await(repository.collection.find(selectorById(e2)).one[Event]) shouldBe Some(e2)
    }
  }

  "insert" should {

    "insert a new document in the collection" in {
      collectionSize shouldBe 0

      await(repository.insert(e)) shouldBe e
      collectionSize              shouldBe 1

      await(repository.collection.find(selectorById(e)).one[Event]) shouldBe Some(e)
    }

    "fail to insert an existing document in the collection" in {
      collectionSize shouldBe 0

      await(repository.insert(e)) shouldBe e
      collectionSize              shouldBe 1

      await(repository.collection.find(selectorById(e)).one[Event]) shouldBe Some(e)

      val updated: Event = e.copy(operator = Operator("user_A", Some("user name")))

      val caught = intercept[DatabaseException] {
        await(repository.insert(updated))
      }
      caught.code shouldBe Some(mongoErrorCode)

      collectionSize                                                      shouldBe 1
      await(repository.collection.find(selectorById(updated)).one[Event]) shouldBe Some(e)
    }
  }

  "search" should {

    "retrieve all expected events from the collection by reference" in {
      val e1 = createNoteEvent("REF_1")
      val e2 = createCaseStatusChangeEvent("REF_2")

      await(repository.insert(e1))
      await(repository.insert(e2))
      collectionSize shouldBe 2

      await(repository.search(EventSearch(Some(Set("REF_1"))), Pagination())) shouldBe Paged(Seq(e1), Pagination(), 1)
      await(repository.search(EventSearch(Some(Set("REF_2"))), Pagination())) shouldBe Paged(Seq(e2), Pagination(), 1)
    }

    "retrieve all expected events from the collection by type" in {
      val e1 = createNoteEvent("REF_1")
      val e2 = createCaseStatusChangeEvent("REF_1")

      await(repository.insert(e1))
      await(repository.insert(e2))
      collectionSize shouldBe 2

      await(repository.search(EventSearch(Some(Set("REF_1")), Some(Set(EventType.NOTE))), Pagination())) shouldBe Paged(
        Seq(e1),
        Pagination(),
        1
      )
      await(repository.search(EventSearch(Some(Set("REF_1")), Some(Set(EventType.CASE_STATUS_CHANGE))), Pagination())) shouldBe Paged(
        Seq(e2),
        Pagination(),
        1
      )
    }

    "retrieve all expected events from the collection by timestamp" in {
      val timestamp1 = LocalDate.of(2019, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant
      val timestamp2 = LocalDate.of(2020, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant
      val e1         = createNoteEvent("REF_1", date = timestamp1)
      val e2         = createCaseStatusChangeEvent("REF_2", date = timestamp2)

      await(repository.insert(e1))
      await(repository.insert(e2))
      collectionSize shouldBe 2

      await(repository.search(EventSearch(timestampMin = Some(timestamp1)), Pagination())) shouldBe Paged(
        Seq(e2, e1),
        Pagination(),
        2
      )
      await(repository.search(EventSearch(timestampMin = Some(timestamp2)), Pagination())) shouldBe Paged(
        Seq(e2),
        Pagination(),
        1
      )
      await(repository.search(EventSearch(timestampMax = Some(timestamp1)), Pagination())) shouldBe Paged(
        Seq(e1),
        Pagination(),
        1
      )
      await(repository.search(EventSearch(timestampMax = Some(timestamp2)), Pagination())) shouldBe Paged(
        Seq(e2, e1),
        Pagination(),
        2
      )
      await(
        repository.search(EventSearch(timestampMin = Some(timestamp1), timestampMax = Some(timestamp2)), Pagination())
      ) shouldBe Paged(Seq(e2, e1), Pagination(), 2)
      await(
        repository.search(EventSearch(timestampMin = Some(timestamp1), timestampMax = Some(timestamp1)), Pagination())
      ) shouldBe Paged(Seq(e1), Pagination(), 1)
      await(
        repository.search(EventSearch(timestampMin = Some(timestamp2), timestampMax = Some(timestamp2)), Pagination())
      ) shouldBe Paged(Seq(e2), Pagination(), 1)
    }

    "retrieve all expected events from the collection sorted by default date descending" in {

      val e20170917 = createNoteEvent("REF_1", Instant.parse("2017-09-17T20:53:31Z"))
      val e20170911 = createNoteEvent("REF_1", Instant.parse("2017-09-11T20:53:31Z"))
      val e20180811 = createNoteEvent("REF_1", Instant.parse("2018-08-11T20:53:31Z"))

      await(repository.insert(e20170911))
      await(repository.insert(e20170917))
      await(repository.insert(e20180811))

      collectionSize shouldBe 3

      val result: Paged[Event] = await(repository.search(EventSearch(Some(Set("REF_1"))), Pagination()))

      result.results.map(_.id) should contain theSameElementsInOrderAs Seq(e20180811.id, e20170917.id, e20170911.id)
    }

    "return an empty sequence when there are no events matching the case reference" in {
      await(repository.insert(createCaseStatusChangeEvent("REF_1")))
      collectionSize shouldBe 1

      await(repository.search(EventSearch(Some(Set("REF_2"))), Pagination())) shouldBe Paged.empty
    }

    "return some events with default Pagination" in {
      await(repository.insert(createAssignmentChangeEvent("ref")))
      await(repository.insert(createNoteEvent("ref")))
      await(repository.search(EventSearch(), Pagination())).size shouldBe 2
    }

    "return up to 'pageSize' cases" in {
      await(repository.insert(createCaseStatusChangeEvent("ref")))
      await(repository.insert(createQueueChangeEvent("ref")))
      await(repository.search(EventSearch(), Pagination(pageSize = 1))).size shouldBe 1
    }

    "return pages of cases" in {
      await(repository.insert(createExtendedUseStatusChangeEvent("ref")))
      await(repository.insert(createNoteEvent("ref")))
      await(repository.search(EventSearch(), Pagination(pageSize = 1))).size shouldBe 1
      await(repository.search(EventSearch(), Pagination(page     = 2, pageSize = 1))).size shouldBe 1
      await(repository.search(EventSearch(), Pagination(page     = 3, pageSize = 1))).size shouldBe 0
    }

  }

  "The 'events' collection" should {

    "have a unique index based on the field 'id' " in {
      val eventId = RandomGenerator.randomUUID()
      val e1      = e.copy(id = eventId)
      await(repository.insert(e1))
      collectionSize shouldBe 1

      val caught = intercept[DatabaseException] {
        val e2 = e1.copy(caseReference = "DEF", operator = Operator("user_123", Some("user name")))
        await(repository.insert(e2))
      }

      caught.code shouldBe Some(mongoErrorCode)

      collectionSize shouldBe 1
    }

    "have all expected indexes" in {

      import scala.concurrent.duration._

      val expectedIndexes = List(
        Index(key = Seq("id"            -> Ascending), name = Some("id_Index"), unique = true),
        Index(key = Seq("caseReference" -> Ascending), name = Some("caseReference_Index"), unique = false),
        Index(key = Seq("type"          -> Ascending), name = Some("type_Index"), unique = false),
        Index(key = Seq("_id"           -> Ascending), name = Some("_id_"))
      )

      val repo = createMongoRepo
      await(repo.ensureIndexes)

      eventually(timeout(5.seconds), interval(100.milliseconds)) {
        assertIndexes(expectedIndexes.sorted, getIndexes(repo.collection).sorted)
      }

      await(repo.drop)
    }

  }

  private def selectorById(e: Event) =
    BSONDocument("id" -> e.id)

}
