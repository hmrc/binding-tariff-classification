/*
 * Copyright 2026 HM Revenue & Customs
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

import org.mongodb.scala.bson.{BsonDocument, BsonNumber}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

@Singleton
class KeywordCountCache @Inject() ()(implicit ec: ExecutionContext) {

  @volatile private var cachedCount: Option[Long] = None
  @volatile private var expiresAt: Long           = 0

  private val ttlMillis = 5.minutes.toMillis

  def getOrUpdate(loader: => Future[Long]): Future[Long] = {
    val now = System.currentTimeMillis()

    if (cachedCount.isDefined && now < expiresAt) {
      Future.successful(cachedCount.get)
    } else {
      loader.map { count =>
        cachedCount = Some(count)
        expiresAt = now + ttlMillis
        count
      }
    }
  }
}
