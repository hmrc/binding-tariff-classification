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

package uk.gov.hmrc.bindingtariffclassification.model

import java.time.Instant

import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.bindingtariffclassification.model.ApplicationType.ApplicationType
import uk.gov.hmrc.bindingtariffclassification.model.PseudoCaseStatus.PseudoCaseStatus

case class CaseFilter
(
  reference: Option[Set[String]] = None,
  applicationType: Option[ApplicationType] = None,
  queueId: Option[String] = None,
  eori: Option[String] = None,
  assigneeId: Option[String] = None,
  statuses: Option[Set[PseudoCaseStatus]] = None,
  traderName: Option[String] = None,
  minDecisionEnd: Option[Instant] = None,
  commodityCode: Option[String] = None,
  decisionDetails: Option[String] = None,
  keywords: Option[Set[String]] = None
)

object CaseFilter {

  private val referenceKey = "reference"
  private val applicationTypeKey = "application_type"
  private val queueIdKey = "queue_id"
  private val eoriKey = "eori"
  private val assigneeIdKey = "assignee_id"
  private val statusKey = "status"
  private val traderNameKey = "trader_name"
  private val minDecisionEndKey = "min_decision_end"
  private val commodityCodeKey = "commodity_code"
  private val decisionDetailsKey = "decision_details"
  private val keywordKey = "keyword"

  implicit def bindable(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[CaseFilter] = new QueryStringBindable[CaseFilter] {

    override def bind(key: String, requestParams: Map[String, Seq[String]]): Option[Either[String, CaseFilter]] = {
      import uk.gov.hmrc.bindingtariffclassification.model.utils.BinderUtil._
      implicit val rp: Map[String, Seq[String]] = requestParams

      Some(
        Right(
          CaseFilter(
            reference = params(referenceKey),
            applicationType = param(applicationTypeKey).flatMap(bindApplicationType),
            queueId = param(queueIdKey),
            eori = param(eoriKey),
            assigneeId = param(assigneeIdKey),
            statuses = params(statusKey).map(_.map(bindPseudoCaseStatus).filter(_.isDefined).map(_.get)),
            traderName = param(traderNameKey),
            minDecisionEnd = param(minDecisionEndKey).flatMap(bindInstant),
            commodityCode = param(commodityCodeKey),
            decisionDetails = param(decisionDetailsKey),
            keywords = params(keywordKey).map(_.map(_.toUpperCase))
          )
        )
      )
    }

    override def unbind(key: String, filter: CaseFilter): String = {
      Seq(
        filter.reference.map(_.map(s => stringBinder.unbind(referenceKey, s.toString)).mkString("&")),
        filter.applicationType.map(t => stringBinder.unbind(applicationTypeKey, t.toString)),
        filter.queueId.map(stringBinder.unbind(queueIdKey, _)),
        filter.eori.map(stringBinder.unbind(eoriKey, _)),
        filter.assigneeId.map(stringBinder.unbind(assigneeIdKey, _)),
        filter.statuses.map(_.map(s => stringBinder.unbind(statusKey, s.toString)).mkString("&")),
        filter.traderName.map(stringBinder.unbind(traderNameKey, _)),
        filter.minDecisionEnd.map(i => stringBinder.unbind(minDecisionEndKey, i.toString)),
        filter.commodityCode.map(stringBinder.unbind(commodityCodeKey, _)),
        filter.decisionDetails.map(stringBinder.unbind(decisionDetailsKey, _)),
        filter.keywords.map(_.map(s => stringBinder.unbind(keywordKey, s.toString)).mkString("&"))
      ).filter(_.isDefined).map(_.get).mkString("&")
    }
  }
}