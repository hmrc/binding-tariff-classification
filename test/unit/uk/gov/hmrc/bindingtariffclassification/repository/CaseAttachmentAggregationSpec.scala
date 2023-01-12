/*
 * Copyright 2023 HM Revenue & Customs
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

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.Case
import uk.gov.hmrc.mongo.test.MongoSupport
import util.CaseData.{createAttachment, createBTIApplicationWithAllFields, createCase, createDecision}

import scala.concurrent.ExecutionContext.Implicits.global

class CaseAttachmentAggregationSpec
    extends BaseMongoIndexSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with MongoSupport {
  self =>

  private val config      = mock[AppConfig]
  private val repository  = newMongoRepository
  private val aggregation = newMongoAggregation

  private def newMongoRepository: CaseMongoRepository =
    new CaseMongoRepository(config, mongoComponent, new SearchMapper(config), new UpdateMapper)

  private def newMongoAggregation: CaseAttachmentAggregation =
    new CaseAttachmentAggregation(mongoComponent)

  private val attachment1           = createAttachment
  private val attachment2           = createAttachment
  private val attachment3           = createAttachment
  private val applicationPdf        = createAttachment
  private val decisionPdf           = createAttachment
  private val letterOfAuthorization = createAttachment

  private val caseWithAttachments: Case = createCase(
    attachments = Seq(attachment1, attachment2, attachment3),
    app = createBTIApplicationWithAllFields(
      applicationPdf        = Some(applicationPdf),
      letterOfAuthorization = Some(letterOfAuthorization)
    ),
    decision = Some(createDecision(decisionPdf = Some(decisionPdf)))
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(repository.deleteAll())
  }

  override def afterAll(): Unit = {
    super.afterAll()
    await(repository.deleteAll())
  }

  private def collectionSize: Int =
    await(
      repository.collection.countDocuments().toFuture().map(f => f.toInt)
    )

  "find" should {
    "return None when there is no matching attachment" in {
      await(repository.insert(caseWithAttachments))
      await(aggregation.refresh())
      collectionSize shouldBe 1

      await(aggregation.find("not-an-id")) shouldBe None
    }

    "return attachments from the attachments field" in {
      await(repository.insert(caseWithAttachments))
      await(aggregation.refresh())
      collectionSize shouldBe 1

      await(aggregation.find(attachment1.id)) shouldBe Some(attachment1)
      await(aggregation.find(attachment2.id)) shouldBe Some(attachment2)
      await(aggregation.find(attachment3.id)) shouldBe Some(attachment3)
    }

    "return attachments from the application.agent.letterOfAuthorization field" in {
      await(repository.insert(caseWithAttachments))
      await(aggregation.refresh())
      collectionSize shouldBe 1

      await(aggregation.find(letterOfAuthorization.id)) shouldBe Some(letterOfAuthorization)
    }

    "return attachments from the application.applicationPdf field" in {
      await(repository.insert(caseWithAttachments))
      await(aggregation.refresh())
      collectionSize shouldBe 1

      await(aggregation.find(applicationPdf.id)) shouldBe Some(applicationPdf)
    }

    "return attachments from the decision.decisionPdf field" in {
      await(repository.insert(caseWithAttachments))
      await(aggregation.refresh())
      collectionSize shouldBe 1

      await(aggregation.find(decisionPdf.id)) shouldBe Some(decisionPdf)
    }
  }

}
