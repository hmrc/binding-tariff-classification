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

package uk.gov.hmrc.bindingtariffclassification.controllers

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.Json.toJson
import play.api.http.Status._
import play.api.mvc.Result
import play.api.test.FakeRequest
import reactivemongo.bson.BSONDocument
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.{ApplicationType, NewTeamRequest, Team}
import uk.gov.hmrc.bindingtariffclassification.service.TeamService
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters._

import scala.concurrent.Future.{failed, successful}

class TeamControllerSpec extends BaseSpec with BeforeAndAfterEach {

  override protected def beforeEach() =
    Mockito.reset(teamService)

  private val team1 = Team(
    id = "1", name = "team1",
    caseTypes = List(ApplicationType.BTI, ApplicationType.LIABILITY_ORDER),
    managers = List("PID1")
  )

  private val team1Updated = Team(
    id = "1", name = "team1",
    caseTypes = List(ApplicationType.BTI, ApplicationType.LIABILITY_ORDER),
    managers = List("PID1", "PID2", "PID3", "PID4", "PID5")
  )

  private val teamService: TeamService = mock[TeamService]
  private val appConfig   = mock[AppConfig]

  private val fakeRequest = FakeRequest()

  private val controller = new TeamController(appConfig, teamService, parser, mcc)

  private val newTeam : NewTeamRequest = NewTeamRequest(
    team1
  )

  "create()" should {
    "create a new team" in {

      when(teamService.insert(any[Team])).thenReturn(successful(team1))

      val result: Result = await(controller.create()(fakeRequest.withBody(toJson(newTeam))))

      status(result) shouldBe CREATED
      jsonBodyOf(result) shouldEqual toJson(team1)
    }

    "return 400 when the JSON request payload is not a Team" in {
      val body   = """{"nonExistingField":"1234"}"""
      val result = await(controller.create()(fakeRequest.withBody(toJson(body))))

      status(result) shouldEqual BAD_REQUEST
    }

    "return 500 when an error occurred" in {
      val error = new DatabaseException {
        override def originalDocument: Option[BSONDocument] = None
        override def code: Option[Int]                      = Some(11000)
        override def message: String                        = "duplicate value for db index"
      }

      when(teamService.insert(any[Team])).thenReturn(failed(error))

      val result = await(controller.create()(fakeRequest.withBody(toJson(newTeam))))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
      jsonBodyOf(result).toString() shouldEqual """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }

  }

  "getById()" should {

    "return 200 with the expected case" in {
      when(teamService.getById(team1.id)).thenReturn(successful(Some(team1)))

      val result = await(controller.getById(team1.id)(fakeRequest))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual toJson(team1)
    }

    "return 404 if there are no teams for the specific id" in {
      when(teamService.getById(team1.id)).thenReturn(successful(None))

      val result = await(controller.getById(team1.id)(fakeRequest))

      status(result) shouldEqual NOT_FOUND
      jsonBodyOf(result).toString() shouldEqual """{"code":"NOT_FOUND","message":"Team not found"}"""
    }

    "return 500 when an error occurred" in {
      val error = new RuntimeException

      when(teamService.getById(team1.id)).thenReturn(failed(error))

      val result = await(controller.getById(team1.id)(fakeRequest))

      status(result) shouldEqual INTERNAL_SERVER_ERROR
      jsonBodyOf(result).toString() shouldEqual """{"code":"UNKNOWN_ERROR","message":"An unexpected error occurred"}"""
    }
  }

  "update()" should {

    "return 200 if the team is updated successfully" in {

      when(teamService.update(team1, true)).thenReturn(successful(Some(team1)))

      val result = await(controller.update(team1.id)(fakeRequest.withBody(toJson(team1))))

      status(result) shouldEqual OK
      jsonBodyOf(result) shouldEqual toJson(team1)
    }
  }
}
