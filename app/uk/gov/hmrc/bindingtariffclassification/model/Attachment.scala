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

package uk.gov.hmrc.bindingtariffclassification.model

case class Attachment
(
 // TODO: we need endpoints (POST, PUT, DELETE) for managing attachments
  application: Boolean, // if the attachment was sent by the trader in the original BTI application
  public: Boolean, // if the attachment is publicly viewable in the public rulings UI
  url: String,
  mimeType: String
// timestamp: ZonedDateTime = ZonedDateTime.now() // TODO: validate if needed
)
