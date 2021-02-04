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

package uk.gov.hmrc.bindingtariffclassification.service

import akka.stream.Materializer
import javax.inject.Inject
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.Team
import uk.gov.hmrc.bindingtariffclassification.repository.TeamRepository

import scala.concurrent.{ExecutionContext, Future}

class TeamService @Inject()(
                             appConfig: AppConfig,
                             teamRepository: TeamRepository
                           )(implicit mat: Materializer) {

  implicit val ec: ExecutionContext = mat.executionContext

  def insert(t: Team): Future[Team] = teamRepository.insert(t)

  def update(t: Team, upsert: Boolean): Future[Option[Team]] =
    teamRepository.update(t, upsert)

  def getById(id: String): Future[Option[Team]] = teamRepository.getById(id)

}
