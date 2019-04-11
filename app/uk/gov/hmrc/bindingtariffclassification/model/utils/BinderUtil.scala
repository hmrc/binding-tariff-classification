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

package uk.gov.hmrc.bindingtariffclassification.model.utils

import java.time.Instant

import uk.gov.hmrc.bindingtariffclassification.model.ApplicationType.ApplicationType
import uk.gov.hmrc.bindingtariffclassification.model.PseudoCaseStatus.PseudoCaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.reporting.CaseReportField.CaseReportField
import uk.gov.hmrc.bindingtariffclassification.model.reporting.CaseReportGroup.CaseReportGroup
import uk.gov.hmrc.bindingtariffclassification.model.reporting.{CaseReportField, CaseReportGroup}
import uk.gov.hmrc.bindingtariffclassification.model.{ApplicationType, PseudoCaseStatus}
import uk.gov.hmrc.bindingtariffclassification.sort.CaseSortField.CaseSortField
import uk.gov.hmrc.bindingtariffclassification.sort.SortDirection.SortDirection
import uk.gov.hmrc.bindingtariffclassification.sort.{CaseSortField, SortDirection}

import scala.util.Try

object BinderUtil {

  def bindCaseReportGroup(value: String): Option[CaseReportGroup] = {
    CaseReportGroup.values.find(_.toString == value)
  }

  def bindCaseReportField(value: String): Option[CaseReportField] = {
    CaseReportField.values.find(_.toString == value)
  }

  def bindSortField(value: String): Option[CaseSortField] = {
    CaseSortField.values.find(_.toString == value)
  }

  def bindSortDirection(value: String): Option[SortDirection] = {
    SortDirection.values.find(_.toString == value)
  }

  def bindPseudoCaseStatus(value: String): Option[PseudoCaseStatus] = {
    PseudoCaseStatus.values.find(_.toString.equalsIgnoreCase(value))
  }

  def bindApplicationType(value: String): Option[ApplicationType] = {
    ApplicationType.values.find(_.toString.equalsIgnoreCase(value))
  }

  def bindInstant(value: String): Option[Instant] = Try(Instant.parse(value)).toOption

  def params(name: String)(implicit requestParams: Map[String, Seq[String]]): Option[Set[String]] = {
    requestParams.get(name).map(_.flatMap(_.split(",")).toSet).filterNot(_.exists(_.isEmpty))
  }

  def param(name: String)(implicit requestParams: Map[String, Seq[String]]): Option[String] = {
    params(name).map(_.head)
  }

}
