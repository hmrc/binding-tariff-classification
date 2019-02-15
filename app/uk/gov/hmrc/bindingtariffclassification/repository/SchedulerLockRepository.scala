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

package uk.gov.hmrc.bindingtariffclassification.repository

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.Logger
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatSchedulerRunEvent
import uk.gov.hmrc.bindingtariffclassification.model.{MongoFormatters, SchedulerRunEvent}
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[SchedulerLockMongoRepository])
trait SchedulerLockRepository {

  def lock(e: SchedulerRunEvent): Future[Boolean]

}

@Singleton
class SchedulerLockMongoRepository @Inject()(mongoDbProvider: MongoDbProvider)
  extends ReactiveRepository[SchedulerRunEvent, BSONObjectID](
    collectionName = "scheduler",
    mongo = mongoDbProvider.mongo,
    domainFormat = MongoFormatters.formatSchedulerRunEvent) with SchedulerLockRepository with MongoCrudHelper[SchedulerRunEvent] {

  override val mongoCollection: JSONCollection = collection

  override def indexes = Seq(
    createCompoundIndex(Seq("name", "runDate"), isUnique = true)
  )

  override def lock(e: SchedulerRunEvent): Future[Boolean] = {
    createOne(e) map { _ =>
      Logger.debug(s"Took Lock for [${e.name}] at [${e.runDate}]")
      true
    } recover { case t: Throwable =>
      Logger.debug(s"Unable to take Lock for [${e.name}] at [${e.runDate}]", t)
      false
    }
  }

}
