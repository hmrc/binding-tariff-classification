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

package uk.gov.hmrc.bindingtariffclassification.controllers

import javax.inject.Singleton
import uk.gov.hmrc.bindingtariffclassification.model.sort.CaseSortField.CaseSortField
import uk.gov.hmrc.bindingtariffclassification.model.sort.SortDirection.SortDirection
import uk.gov.hmrc.bindingtariffclassification.model.sort.{CaseSort, CaseSortField, SortDirection}

@Singleton
class CaseSortMapper {

  def from(plainTextSortField: Option[String], plainTextSortDirection: Option[String]): Option[CaseSort] = {
    val field: Option[CaseSortField] = plainTextSortField.flatMap(sortFieldOf)
    val direction: Option[SortDirection] = plainTextSortDirection.flatMap(directionOf)
    (field, direction) match {
      case (Some(sort: CaseSortField), Some(dir: SortDirection)) => Some(CaseSort(sort, dir))
      case (Some(sort: CaseSortField), None) => Some(CaseSort(sort))
      case _ => None
    }
  }

  private def directionOf(direction: String): Option[SortDirection] = {
    direction match {
      case "ascending" => Some(SortDirection.ASCENDING)
      case "descending" => Some(SortDirection.DESCENDING)
      case _ => None
    }
  }

  private def sortFieldOf(field: String): Option[CaseSortField] = {
    field match {
      case "days-elapsed" => Some(CaseSortField.DAYS_ELAPSED)
      case _ => None
    }
  }

}
