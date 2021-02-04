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
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.{ApplicationType, NewTeamRequest, Team}
import uk.gov.hmrc.bindingtariffclassification.service.TeamService
import uk.gov.hmrc.bindingtariffclassification.model.RESTFormatters._

import scala.concurrent.Future
import scala.concurrent.Future.successful

class TeamControllerSpec extends BaseSpec with BeforeAndAfterEach {

  override protected def beforeEach() =
    Mockito.reset(teamService)

  private val team1 = Team(
    id = "1", name = "team1",
    caseTypes = List(ApplicationType.BTI, ApplicationType.LIABILITY_ORDER),
    managers = List("PID1")
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

  }
}
