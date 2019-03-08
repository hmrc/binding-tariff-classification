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

package uk.gov.hmrc.bindingtariffclassification.sort


object SortField extends Enumeration {
  type SortField = Value

  val DAYS_ELAPSED = Value("days-elapsed")
  val COMMODITY_CODE = Value("commodity-code")
  val CREATED_DATE = Value("created-date")
  val DECISION_START_DATE = Value("decision-start-date")
}
