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

package uk.gov.hmrc.bindingtariffclassification.component.utils

import java.time._

import javax.inject.Inject
import play.api.Configuration
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class AppConfigWithAFixedDate @Inject() (runModeConfiguration: Configuration, config: ServicesConfig)
    extends AppConfig(runModeConfiguration, config) {

  // Get current year dynamically
  private val currentYear = Year.now().getValue

  // Build the fixed date dynamically: February 3rd of current year
  private val defaultSystemDate: Instant = LocalDate.of(currentYear, 2, 3).atStartOfDay().toInstant(ZoneOffset.UTC)
  private val defaultZoneId              = ZoneOffset.UTC
  override lazy val clock: Clock         = Clock.fixed(defaultSystemDate, defaultZoneId)
}
