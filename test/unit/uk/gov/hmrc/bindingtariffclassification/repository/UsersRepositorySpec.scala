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
import org.mongodb.scala.model.Filters
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.hmrc.bindingtariffclassification.model._
import uk.gov.hmrc.mongo.test.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global

class UsersRepositorySpec
    extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with MongoSupport
    with Eventually {
  self =>
  private val mongoErrorCode = 11000

  private val repository = createMongoRepo

  private def createMongoRepo =
    new UsersMongoRepository(mongoComponent)

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.collection.deleteMany(Filters.empty()).toFuture())
    await(repository.ensureIndexes)
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

  "get by id" should {

    "return existing user" in {
      val user = Operator(
        "user-id",
        Some("user"),
        Some("email"),
        Role.CLASSIFICATION_OFFICER
      )
      await(repository.insert(user))
      await(repository.getById("user-id")) shouldBe Some(user)
    }

    "return none if id not found" in {
      await(repository.getById("user-id")) shouldBe None
    }
  }

  "search" should {

    "retrieve all expected users from the collection by role" in {
      val user1 = Operator(
        "id1",
        Some("user"),
        Some("email"),
        Role.CLASSIFICATION_OFFICER
      )
      val user2 = Operator(
        "id2",
        Some("user"),
        Some("email"),
        Role.CLASSIFICATION_OFFICER
      )
      val user3 = Operator(
        "id3",
        Some("user"),
        Some("email"),
        Role.CLASSIFICATION_MANAGER
      )

      await(repository.insert(user1))
      await(repository.insert(user2))
      await(repository.insert(user3))
      collectionSize shouldBe 3

      await(
        repository.search(
          UserSearch(Some(Set(Role.CLASSIFICATION_OFFICER)), None),
          Pagination()
        )
      ) shouldBe
        Paged(Seq(user1, user2), Pagination(), 2)

      await(
        repository.search(
          UserSearch(Some(Set(Role.CLASSIFICATION_MANAGER)), None),
          Pagination()
        )
      ) shouldBe
        Paged(Seq(user3), Pagination(), 1)
    }

    "retrieve all expected users from the collection by team" in {

      val user1 = Operator(
        "id1",
        Some("user"),
        Some("email"),
        Role.CLASSIFICATION_OFFICER,
        List("1")
      )

      val user2 = Operator(
        "id2",
        Some("user"),
        Some("email"),
        Role.CLASSIFICATION_OFFICER,
        List("2")
      )

      val user3 = Operator(
        "id3",
        Some("user"),
        Some("email"),
        Role.CLASSIFICATION_OFFICER,
        List("1")
      )

      await(repository.insert(user1))
      await(repository.insert(user2))
      await(repository.insert(user3))
      collectionSize shouldBe 3

      await(repository.search(UserSearch(None, Some("1")), Pagination())) shouldBe
        Paged(Seq(user1, user3), Pagination(), 2)
    }

    "return an empty sequence when there are no users matching" in {
      val user1 = Operator(
        "id1",
        Some("user"),
        Some("email"),
        Role.CLASSIFICATION_OFFICER,
        List("1")
      )

      val user2 = Operator(
        "id2",
        Some("user"),
        Some("email"),
        Role.CLASSIFICATION_OFFICER,
        List("3")
      )

      val user3 = Operator(
        "id3",
        Some("user"),
        Some("email"),
        Role.CLASSIFICATION_MANAGER,
        List("2")
      )

      await(repository.insert(user1))
      await(repository.insert(user2))
      await(repository.insert(user3))
      collectionSize shouldBe 3

      await(repository.search(UserSearch(Some(Set(Role.CLASSIFICATION_MANAGER)), Some("2")), Pagination())) shouldBe
        Paged(Seq(user3), Pagination(), 1)
    }

    "return all users that matches the query" in {
      val user1 = Operator(
        "id1",
        Some("user"),
        Some("email"),
        Role.CLASSIFICATION_OFFICER,
        List("1")
      )

      await(repository.insert(user1))
      collectionSize shouldBe 1

      await(
        repository.search(UserSearch(Some(Set(Role.READ_ONLY)), None), Pagination())
      ) shouldBe Paged.empty
    }
  }

  "insert" should {
    val user = Operator(
      "user-id",
      Some("user"),
      Some("email"),
      Role.CLASSIFICATION_OFFICER
    )

    "insert a new document in the collection" in {
      val size = collectionSize

      await(repository.insert(user)) shouldBe user
      collectionSize                 shouldBe 1 + size
      await(repository.collection.find(selectorById(user.id)).headOption()) shouldBe Some(
        user
      )
    }

    "fail to insert an existing document in the collection" in {
      await(repository.insert(user)) shouldBe user
      val size = collectionSize

      val caught = intercept[MongoWriteException] {
        await(repository.insert(user))
      }

      caught.getError.getCode shouldBe mongoErrorCode
      collectionSize          shouldBe size
    }
  }

  "update" should {
    val user = Operator(
      "user-id",
      Some("user"),
      Some("email"),
      Role.CLASSIFICATION_OFFICER
    )

    "modify an existing document in the collection" in {
      await(repository.insert(user)) shouldBe user

      val size = collectionSize

      val updatedUser = user.copy(
        name          = Some("updated-name"),
        memberOfTeams = List("ACT", "ELM")
      )
      await(repository.update(updatedUser, upsert = false)) shouldBe Some(
        updatedUser
      )
      collectionSize shouldBe size

      await(
        repository.collection.find(selectorById(updatedUser.id)).headOption()
      ) shouldBe Some(updatedUser)
    }

    "do nothing when trying to update an unknown document" in {
      val size = collectionSize

      await(repository.update(user, upsert = false)) shouldBe None
      collectionSize shouldBe size
    }

    "upsert a new existing document in the collection" in {
      val size = collectionSize

      await(repository.update(user, upsert = true)) shouldBe Some(user)
      collectionSize shouldBe size + 1
    }
  }

  private def selectorById(id: String) = Filters.equal("id", id)

}
