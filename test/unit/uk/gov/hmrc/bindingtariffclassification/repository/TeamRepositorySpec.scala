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
import play.api.libs.json.JsObject
import reactivemongo.api.{Cursor, DB, ReadConcern}
import reactivemongo.bson._
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatTeam
import uk.gov.hmrc.bindingtariffclassification.model.{ApplicationType, Team}
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TeamRepositorySpec
  extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with MongoSpecSupport
    with Eventually {
  self =>

  private val conflict = 11000

  private val mongoDbProvider: MongoDbProvider = new MongoDbProvider {
    override val mongo: () => DB = self.mongo
  }

  private val config = mock[AppConfig]
  private val repository = newMongoRepository

  private def newMongoRepository: TeamMongoRepository =
    new TeamMongoRepository(mongoDbProvider)

  private val team1 = Team(
    id = "1", name = "team1",
    caseTypes = List(ApplicationType.BTI, ApplicationType.LIABILITY_ORDER),
    managers = List("PID1")
  )

  private val team2 = Team(
    id = "2", name = "team2",
    caseTypes = List(ApplicationType.CORRESPONDENCE, ApplicationType.MISCELLANEOUS),
    managers = List("PID1", "PID3")
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  private def collectionSize: Int =
    await(
      repository.collection
        .count(selector = None, limit = Some(0), skip = 0, hint = None, readConcern = ReadConcern.Local)
    ).toInt


  "insert" should {

    "insert a new document in the collection" in {
      val size = collectionSize

      await(repository.insert(team1)) shouldBe team1
      collectionSize shouldBe 1 + size
      await(repository.collection.find(selectorByReference(team1)).one[Team]) shouldBe Some(team1)
    }

    "fail to insert an existing document in the collection" in {
      await(repository.insert(team1)) shouldBe team1
      val size = collectionSize

      val caught = intercept[DatabaseException] {
        await(repository.insert(team1))
      }
      caught.code shouldBe Some(conflict)

      collectionSize shouldBe size
    }

  }

  "update" should {

    "modify an existing document in the collection" in {
      await(repository.insert(team1)) shouldBe team1
      val size = collectionSize

      val updated: Team = team1.copy(managers = List("PID1", "PID2"))
      await(repository.update(updated, upsert = false)) shouldBe Some(updated)
      collectionSize shouldBe size

      await(repository.collection.find(selectorByReference(updated)).one[Team]) shouldBe Some(updated)
    }

    "do nothing when trying to update an unknown document" in {
      val size = collectionSize

      await(repository.update(team1, upsert = false)) shouldBe None
      collectionSize shouldBe size
    }

    "upsert a new existing document in the collection" in {
      val size = collectionSize

      await(repository.update(team1, upsert = true)) shouldBe Some(team1)
      collectionSize shouldBe size + 1
    }
  }

  "get by id" should {

    "return an existing team" in {
      await(repository.insert(team1))
      await(repository.getById("1")) shouldBe Some(team1)
    }

    "return none if id not found" in {
      await(repository.getById("1")) shouldBe None
    }
  }

  protected def getMany(filterBy: JsObject, sortBy: JsObject): Future[Seq[Team]] =
    repository.collection
      .find[JsObject, Team](filterBy)
      .sort(sortBy)
      .cursor[Team]()
      .collect[Seq](Int.MaxValue, Cursor.FailOnError[Seq[Team]]())

  private def selectorByReference(t: Team) =
    BSONDocument("id" -> t.id)

  private def store(teams: Team*): Unit =
    teams.foreach { t: Team => await(repository.insert(t)) }
}
