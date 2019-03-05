/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject._
import uk.gov.hmrc.bindingtariffclassification.model.{Event, Paged, Pagination}
import uk.gov.hmrc.bindingtariffclassification.repository.EventRepository

import scala.concurrent.Future

@Singleton
class EventService @Inject()(repository: EventRepository) {

  def insert(e: Event): Future[Event] = {
    repository.insert(e)
  }

  def getByCaseReference(caseReference: String, pagination: Pagination): Future[Paged[Event]] = {
    repository.getByCaseReference(caseReference, pagination)
  }

  def deleteAll(): Future[Unit] = {
    repository.deleteAll()
  }

}