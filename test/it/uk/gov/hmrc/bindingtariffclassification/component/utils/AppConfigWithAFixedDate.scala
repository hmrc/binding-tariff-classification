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

package uk.gov.hmrc.bindingtariffclassification.component.utils

import java.time._

import javax.inject.Inject
import play.api.Configuration
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class AppConfigWithAFixedDate @Inject() (runModeConfiguration: Configuration, config: ServicesConfig)
    extends AppConfig(runModeConfiguration, config) {
  private val defaultSystemDate: Instant = LocalDate.parse("2019-02-03").atStartOfDay().toInstant(ZoneOffset.UTC)
  private val defaultZoneId              = ZoneId.systemDefault()
  override lazy val clock: Clock         = Clock.fixed(defaultSystemDate, defaultZoneId)
}
