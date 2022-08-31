/*
 * Copyright 2022 HM Revenue & Customs
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

import akka.stream.Materializer
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Aggregates.{`match`, replaceRoot, unwind}
import org.mongodb.scala.model.Filters.notEqual
import org.mongodb.scala.model.{Aggregates, Filters, Projections, UnwindOptions}
import play.api.libs.json.Json
import uk.gov.hmrc.bindingtariffclassification.model.{Attachment, MongoCodecs}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CaseAttachmentAggregation @Inject() (mongoComponent: MongoComponent)(implicit mat: Materializer) {

  implicit val ec: ExecutionContext = mat.executionContext

  private val attachmentArrayFields: Set[String] = Set(
    "attachments"
  )

  private val attachmentFields: Set[String] = Set(
    "application.agent.letterOfAuthorisation",
    "decision.decisionPdf",
    "application.applicationPdf"
  )

  private val allAttachmentFields: Set[String] = attachmentArrayFields ++ attachmentFields

  private val unwindNestedAttachmentArrays = attachmentArrayFields.toList.map(name =>
    unwind(
      "$" + name,
      UnwindOptions()
        .includeArrayIndex(null)
        .preserveNullAndEmptyArrays(true)
    )
  )
  private val projectAttachments = Aggregates.project(
    Projections.fields(
      Projections.computed("attachment", allAttachmentFields.map(name => "$" + name).toSeq)
    )
  )
  private val unwindAttachments =
    unwind(
      "$attachment",
      UnwindOptions()
        .includeArrayIndex(null)
        .preserveNullAndEmptyArrays(false)
    )
  private val filterNotNull = `match`(notEqual("attachment", null))
  private val toRoot        = replaceRoot(Codecs.toBson(Json.obj("$mergeObjects" -> List("$attachment"))))
  private val out           = Aggregates.out("attachments")

  protected val pipeline: List[Bson] = {
    unwindNestedAttachmentArrays ++ List(
      projectAttachments,
      unwindAttachments,
      filterNotNull,
      toRoot,
      out
    )
  }

  def refresh(): Future[Unit] =
    mongoComponent.database
      .getCollection[Attachment]("cases")
      .withCodecRegistry(MongoCodecs.attachment)
      .aggregate(pipeline)
      .toFuture()
      .map(_ => Future.unit)

  def find(attachmentId: String): Future[Option[Attachment]] =
    mongoComponent.database
      .getCollection[Attachment]("attachments")
      .withCodecRegistry(MongoCodecs.attachment)
      .find(Filters.equal("id", attachmentId))
      .first()
      .toFutureOption()
}
