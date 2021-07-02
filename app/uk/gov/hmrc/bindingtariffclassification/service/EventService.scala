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

import org.mongodb.scala.bson.{BsonArray, BsonDateTime, BsonDocument, BsonString, BsonValue}
import uk.gov.hmrc.bindingtariffclassification.model.{Event, EventSearch, Paged, Pagination}
import uk.gov.hmrc.bindingtariffclassification.repository.EventRepository

import javax.inject._
import scala.concurrent.Future
@Singleton
class EventService @Inject() (repository: EventRepository) {

  def insertEvent(e: Event): Future[Event] =
    repository.insertOne(e)

  def searchEvents(search: EventSearch, pagination: Pagination): Future[Paged[Event]] =
    repository.findEvents(search, pagination)

  def deleteAllEvents(): Future[Unit] =
    repository.deleteAll

  def deleteCaseEvents(caseReference: String): Future[Unit] =
    repository.deleteMany(eventSearchToBson(EventSearch(caseReference = Some(Set(caseReference)))))

  private def eventSearchToBson(search: EventSearch): BsonDocument = {
    val caseReference = search.caseReference.fold(Seq.empty[(String,BsonValue)]){ reference =>
      Seq("caseReference" -> BsonArray.fromIterable(reference.map(value => BsonString(value))))}
    val `type` = search.`type`.fold(Seq.empty[(String,BsonValue)]){ event =>
      Seq("type" -> BsonArray.fromIterable(event.map(value => BsonString(value.toString))))}
    val min = search.timestampMin.fold(Seq.empty[(String,BsonValue)]){ timestamp =>
      Seq("timestampMin" -> BsonDateTime(timestamp.toEpochMilli))}
    val max = search.timestampMin.fold(Seq.empty[(String,BsonValue)]){ timestamp =>
      Seq("timestampMax" -> BsonDateTime(timestamp.toEpochMilli))}
    BsonDocument(caseReference ++ `type` ++ min ++ max)
  }
}
