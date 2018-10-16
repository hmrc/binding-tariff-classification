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
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.bindingtariffclassification.model.JsonFormatters.formatCase
import uk.gov.hmrc.bindingtariffclassification.repository.{BaseMongoIndexSpec, CaseMongoRepository, MongoDbProvider}
import uk.gov.hmrc.bindingtariffclassification.todelete.CaseData._
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global

class CaseRepositorySpec extends BaseMongoIndexSpec
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with MongoSpecSupport
  with Eventually {
  self =>

  private val mongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private def getIndexes(repo: CaseMongoRepository): List[Index] = {
    val indexesFuture = repo.collection.indexesManager.list()
    await(indexesFuture)
  }

  private val repository = new CaseMongoRepository(mongoDbProvider)

  private val case1: Case = createCase()
  private val case2: Case = createCase()

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

      await(repository.insert(case1)) shouldBe case1
      collectionSize shouldBe 1
      await(repository.collection.find(selectorByReference(case1)).one[Case]) shouldBe Some(case1)
    }

    "fail to insert an existing document in the collection" in {
      collectionSize shouldBe 0

      await(repository.insert(case1)) shouldBe case1
      collectionSize shouldBe 1

      val caught = intercept[DatabaseException] {
        await(repository.insert(case1))
      }
      caught.code shouldBe Some(11000)

      collectionSize shouldBe 1
    }

  }

  "update" should {

    "modify an existing document in the collection" in {
      collectionSize shouldBe 0

      await(repository.insert(case1)) shouldBe case1
      collectionSize shouldBe 1

      val updated: Case = case1.copy(application = createBTIApplication, status = CaseStatus.CANCELLED)
      await(repository.update(updated)) shouldBe Some(updated)
      collectionSize shouldBe 1

      await(repository.collection.find(selectorByReference(updated)).one[Case]) shouldBe Some(updated)
    }

    "do nothing when try to update a non existing document in the collection" in {
      collectionSize shouldBe 0

      await(repository.update(case1)) shouldBe None
      collectionSize shouldBe 0
    }
  }


  "getAll" should {

    "retrieve all cases from the collection" in {
      await(repository.insert(case1))
      await(repository.insert(case2))
      collectionSize shouldBe 2

      await(repository.getAll) shouldBe Seq(case1, case2)
    }

    "return an empty sequence when there are no cases in the collection" in {
      await(repository.getAll) shouldBe Seq.empty
    }
  }


  "getByReference" should {

    "retrieve the correct record" in {
      await(repository.insert(case1))
      collectionSize shouldBe 1

      await(repository.getByReference(case1.reference)) shouldBe Some(case1)
    }

    "return 'None' when the 'reference' doesn't match any record in the collection" in {
      for (_ <- 1 to 3) {
        await(repository.insert(createCase()))
      }
      collectionSize shouldBe 3

      await(repository.getByReference("WRONG_REFERENCE")) shouldBe None
    }
  }

  "The 'cases' collection" should {

    "have a unique index based on the field 'reference' " in {
      await(repository.insert(case1))
      collectionSize shouldBe 1

      val caught = intercept[DatabaseException] {

        await(repository.insert(case1.copy(status = CaseStatus.REFERRED)))
      }
      caught.code shouldBe Some(11000)

      collectionSize shouldBe 1
    }

    "have all expected indexes" in {

      import scala.concurrent.duration._

      val expectedIndexes = List(
        Index(key = Seq("reference" -> Ascending), name = Some("reference_Index"), unique = true, background = true),
        Index(key = Seq("_id" -> Ascending), name = Some("_id_"))
      )

      val repo = new CaseMongoRepository(mongoDbProvider)

      eventually(timeout(5.seconds), interval(100.milliseconds)) {
        assertIndexes(expectedIndexes.sorted, getIndexes(repo).sorted)
      }
    }
  }

  private def selectorByReference(c: Case) = {
    BSONDocument("reference" -> c.reference)
  }

}
