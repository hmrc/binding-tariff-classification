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
import reactivemongo.api.{DB, ReadConcern}
import reactivemongo.bson.{BSONDocument, _}
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters._
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global

class KeywordRepositorySpec
    extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with MongoSpecSupport
    with Eventually {
  self =>
  private val mongoErrorCode = 11000

  private def selectorByName(name: String) =
    BSONDocument("name" -> name)

  private val keyword  = Keyword("keyword name", approved = true)
  private val keyword2 = Keyword(name                     = "lentil", approved = true)

  private val mongoDbProvider: MongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private val repository = createMongoRepo

  private def createMongoRepo =
    new KeywordsMongoRepository(mongoDbProvider)

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

  private def collectionSize: Int =
    await(
      repository.collection
        .count(
          selector    = None,
          limit       = Some(0),
          skip        = 0,
          hint        = None,
          readConcern = ReadConcern.Local
        )
    ).toInt

  "insert" should {
    "insert a new document in the collection" in {
      val size = collectionSize

      await(repository.insert(keyword)) shouldBe keyword
      collectionSize                    shouldBe 1 + size
      await(
        repository.collection.find(selectorByName(keyword.name)).one[Keyword]
      ) shouldBe Some(keyword)
    }

    "fail to insert an existing document in the collection" in {
      await(repository.insert(keyword)) shouldBe keyword
      val size = collectionSize

      val caught = intercept[DatabaseException] {
        await(repository.insert(keyword))
      }

      caught.code    shouldBe Some(mongoErrorCode)
      collectionSize shouldBe size
    }
  }

  "delete" should {
    "remove the entry from the Collection" in {

      val keyword  = Keyword("potatoes", approved = true)
      val keyword2 = Keyword("rice", approved     = true)

      await(repository.insert(keyword))
      await(repository.insert(keyword2))

      collectionSize shouldBe 2

      await(repository.delete("potatoes"))
      collectionSize shouldBe 1
      await(
        repository.collection.find(selectorByName(keyword2.name)).one[Keyword]
      ) shouldBe Some(keyword2)

    }
  }

  "update" should {
    val keyword = Keyword(name = "word", approved = true)

    "modify an existing keyword (approved field)" in {
      await(repository.insert(keyword)) shouldBe keyword

      val size = collectionSize

      val updatedKeyword = keyword.copy(name = "word", approved = false)
      await(repository.update(updatedKeyword, upsert = false)) shouldBe Some(
        updatedKeyword
      )
      collectionSize shouldBe size

      await(
        repository.collection
          .find(selectorByName(updatedKeyword.name))
          .one[Keyword]
      ) shouldBe Some(updatedKeyword)
    }

    "do nothing when trying to update an unknown document" in {
      val size = collectionSize

      await(repository.update(keyword, upsert = false)) shouldBe None
      collectionSize shouldBe size
    }

    "upsert a new existing document in the collection" in {
      val size = collectionSize

      await(repository.update(keyword, upsert = true)) shouldBe Some(keyword)
      collectionSize shouldBe size + 1
    }
  }

  "findAll" should {

    "get all keywords" in {
      await(repository.insert(keyword))
      await(repository.insert(keyword2))
      collectionSize shouldBe 2

      await(repository.findAll(Pagination())) shouldBe Paged(
        Seq(keyword, keyword2),
        Pagination(),
        2
      )
    }

    "return an empty sequence when there are no keywords in the collection" in {
      await(repository.insert(keyword))
      collectionSize shouldBe 1

      await(repository.delete(keyword.name))
      collectionSize shouldBe 0

      await(repository.findAll(Pagination())) shouldBe
        Paged(Seq.empty, Pagination(), 0)
    }
  }

}
