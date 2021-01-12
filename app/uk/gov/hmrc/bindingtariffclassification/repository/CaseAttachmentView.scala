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

import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import play.api.libs.json.{JsNull, JsObject, Json}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.commands.JSONAggregationFramework
import reactivemongo.play.json.commands.JSONAggregationFramework.{Match, Project, ReplaceRoot, Unwind}
import uk.gov.hmrc.bindingtariffclassification.model.Attachment
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatAttachment

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[CaseAttachmentMongoView])
trait CaseAttachmentView {
  def find(attachmentId: String): Future[Option[Attachment]]
}

@Singleton
class CaseAttachmentMongoView @Inject() (
  mongoDbProvider: MongoDbProvider
) extends ReactiveView[Attachment, BSONObjectID](
      viewName       = "caseAttachments",
      collectionName = "cases",
      mongo          = mongoDbProvider.mongo
    )
    with CaseAttachmentView {

  private val attachmentArrayFields: Set[String] = Set(
    "attachments"
  )

  private val attachmentFields: Set[String] = Set(
    "application.agent.letterOfAuthorisation",
    "decision.decisionPdf",
    "application.applicationPdf"
  )

  private val allAttachmentFields: Set[String] = attachmentArrayFields ++ attachmentFields

  override protected val pipeline: Seq[JSONAggregationFramework.PipelineOperator] = {
    val unwindNestedAttachmentArrays = attachmentArrayFields.toSeq.map(name => Unwind(name, None, Some(true)))

    val projectAttachments = Project(
      Json.obj(
        "attachment" -> allAttachmentFields.map(name => "$" + name)
      )
    )
    val unwindAttachments = Unwind("attachment", None, Some(false))
    val filterNotNull     = Match(Json.obj("attachment" -> Json.obj("$ne" -> JsNull)))
    val toRoot            = ReplaceRoot(Json.obj("$mergeObjects" -> List("$attachment")))

    unwindNestedAttachmentArrays ++ Seq(
      projectAttachments,
      unwindAttachments,
      filterNotNull,
      toRoot
    )
  }

  override def find(attachmentId: String): Future[Option[Attachment]] =
    view
      .find[JsObject, Attachment](selector = Json.obj("id" -> attachmentId), projection = None)
      .one[Attachment]
}
