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

package uk.gov.hmrc.bindingtariffclassification.service

import javax.inject._
import uk.gov.hmrc.bindingtariffclassification.model.Case
import uk.gov.hmrc.bindingtariffclassification.repository.CaseRepository

import scala.concurrent.Future

@Singleton
class CaseService @Inject()(repository: CaseRepository) {

  def insert(c: Case): Future[Case] = {
    repository.insert(c)
  }

  def update(c:Case): Future[Option[Case]] = {
    repository.update(c)
  }

  def getByReference(reference: String): Future[Option[Case]] = {
    repository.getByReference(reference)
  }

  def getAll: Future[Seq[Case]] = {
    repository.getAll
  }
}
