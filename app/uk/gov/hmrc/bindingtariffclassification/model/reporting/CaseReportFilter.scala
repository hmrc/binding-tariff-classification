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

package uk.gov.hmrc.bindingtariffclassification.model.reporting

import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.bindingtariffclassification.model.ApplicationType.ApplicationType
import uk.gov.hmrc.bindingtariffclassification.model.CaseStatus.CaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.utils.BinderUtil._
import uk.gov.hmrc.bindingtariffclassification.model.{ApplicationType, CaseStatus}

case class CaseReportFilter
(
  decisionStartDate: Option[InstantRange] = None,
  referralDate: Option[InstantRange] = None,
  reference: Option[Set[String]] = None,
  status: Option[Set[CaseStatus]] = None,
  applicationType: Option[Set[ApplicationType]] = None,
  assigneeId: Option[String] = None
)

object CaseReportFilter {

  val decisionStartKey = "decision_start"
  val referralDateKey = "referral_date"
  val referenceKey = "reference"
  val statusKey = "status"
  val applicationTypeKey = "application_type"
  val assigneeIdKey = "assignee_id"

  implicit def binder(implicit
                      rangeBinder: QueryStringBindable[InstantRange],
                      stringBinder: QueryStringBindable[String]): QueryStringBindable[CaseReportFilter] = new QueryStringBindable[CaseReportFilter] {

    override def bind(key: String, requestParams: Map[String, Seq[String]]): Option[Either[String, CaseReportFilter]] = {
      implicit val rp: Map[String, Seq[String]] = requestParams

      val decisionStart: Option[InstantRange] = rangeBinder.bind(decisionStartKey, requestParams).filter(_.isRight).map(_.right.get)
      val referralDate: Option[InstantRange] = rangeBinder.bind(referralDateKey, requestParams).filter(_.isRight).map(_.right.get)
      val reference: Option[Set[String]] = params(referenceKey)
      val status: Option[Set[CaseStatus]] = params(statusKey).map(_.map(CaseStatus.withName))
      val applicationType: Option[Set[ApplicationType]] = params(applicationTypeKey).map(_.map(ApplicationType.withName))
      val assigneeId: Option[String] = param(assigneeIdKey)

      Some(
        Right(
          CaseReportFilter(
            decisionStart,
            referralDate,
            reference,
            status,
            applicationType,
            assigneeId
          )
        )
      )
    }

    override def unbind(key: String, filter: CaseReportFilter): String = {
      Seq(
        filter.decisionStartDate.map(r => rangeBinder.unbind(decisionStartKey, r)),
        filter.referralDate.map(r => rangeBinder.unbind(referralDateKey, r)),
        filter.reference.map(_.map(r => stringBinder.unbind(referenceKey, r)).mkString("&")),
        filter.status.map(_.map(r => stringBinder.unbind(statusKey, r.toString)).mkString("&")),
        filter.applicationType.map(_.map(r => stringBinder.unbind(applicationTypeKey, r.toString)).mkString("&")),
        filter.assigneeId.map(r => stringBinder.unbind(assigneeIdKey, r))
      ).filter(_.isDefined).map(_.get).mkString("&")
    }
  }
}
