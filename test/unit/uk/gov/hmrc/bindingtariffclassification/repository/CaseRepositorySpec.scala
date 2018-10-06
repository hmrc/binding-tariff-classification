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
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.JsonFormatters.formatCase
import uk.gov.hmrc.bindingtariffclassification.repository.{CaseMongoRepository, MongoDbProvider}
import uk.gov.hmrc.bindingtariffclassification.todelete.CaseData._
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class CaseRepositorySpec extends UnitSpec
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with MongoSpecSupport
  with Eventually { self =>

  private val mongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private def getIndexes(repo: CaseMongoRepository): List[Index] = {
    val indexesFuture = repo.collection.indexesManager.list()
    await(indexesFuture)
  }

  private val repository = new CaseMongoRepository(mongoDbProvider)

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

  "insertOrUpdate" should {

    val c: Case = createCase()

    "insert a new document in the collection" in {
      collectionSize shouldBe 0

      await(repository.insertOrUpdate(c)) shouldBe ((true, c))
      collectionSize shouldBe 1
      await(repository.collection.find(selectorByReference(c)).one[Case]) shouldBe Some(c)
    }

    "update the existing document in the collection" in {
      collectionSize shouldBe 0

      await(repository.insertOrUpdate(c)) shouldBe ((true, c))
      collectionSize shouldBe 1

      val updated: Case = c.copy(application = createBTIApplication, status = CaseStatus.CANCELLED)
      await(repository.insertOrUpdate(updated)) shouldBe ((false, updated))
      collectionSize shouldBe 1
      await(repository.collection.find(selectorByReference(updated)).one[Case]) shouldBe Some(updated)
    }
  }

  "getAll" should {

    "retrieve all cases from the collection" in {
      val c1 = createCase()
      val c2 = createCase()

      await(repository.insertOrUpdate(c1))
      await(repository.insertOrUpdate(c2))
      collectionSize shouldBe 2

      await(repository.getAll) shouldBe Seq(c1, c2)
    }

    "return an empty sequence when there are no cases in the collection" in {
      await(repository.getAll) shouldBe Seq()
    }
  }

  "getByReference" should {

    "retrieve the correct record" in {
      val c: Case = createCase()
      await(repository.insertOrUpdate(c))
      collectionSize shouldBe 1

      await(repository.getByReference(c.reference)) shouldBe Some(c)
    }

    "return 'None' when the 'reference' doesn't match any record in the collection" in {
      for (_ <- 1 to 3) {
        await(repository.insertOrUpdate(createCase()))
      }
      collectionSize shouldBe 3

      await(repository.getByReference("WRONG_REFERENCE")) shouldBe None
    }
  }

  "The 'cases' collection" should {

    "have a unique index based on the field 'reference' " in {
      val c1 = createCase()
      await(repository.insertOrUpdate(c1))
      collectionSize shouldBe 1

      await(repository.insertOrUpdate(c1))
      collectionSize shouldBe 1
    }

    "have all expected indexes" in {

      import scala.concurrent.duration._

      val indexVersion = Some(1)
      val expectedIndexes = List(
        Index(key = Seq("reference" -> Ascending), name = Some("reference_Index"), unique = true, background = true, version = indexVersion),
        Index(key = Seq("_id" -> Ascending), name = Some("_id_"), version = indexVersion)
      )

      val repo = new CaseMongoRepository(mongoDbProvider)

      eventually(timeout(5.seconds), interval(100.milliseconds)) {
        getIndexes(repo).toSet shouldBe expectedIndexes.toSet
      }

      await(repo.drop) shouldBe true
    }
  }

  private def selectorByReference(c: Case) = {
    BSONDocument("reference" -> c.reference)
  }

}
