/*
 * Copyright 2025 HM Revenue & Customs
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

import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.model.filestore.FileSearch

class FileSearchSpec extends BaseSpec with MockitoSugar {

  "FileSearch" when {

    ".bind" should {

      "query params for id and published status" in {

        val actual: Option[Either[String, FileSearch]] =
          FileSearch.bindable.bind("id", Map[String, Seq[String]]("id" -> Seq("fakeId"), "published" -> Seq("true")))

        actual shouldBe Some(Right(FileSearch(Some(Set("fakeId")), Some(true))))
      }
    }
  }
}
