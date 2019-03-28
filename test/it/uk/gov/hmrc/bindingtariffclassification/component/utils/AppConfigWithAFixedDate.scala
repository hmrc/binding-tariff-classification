package uk.gov.hmrc.bindingtariffclassification.component.utils

import java.time._

import javax.inject.Inject
import play.api.{Configuration, Environment}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig


class AppConfigWithAFixedDate @Inject()(runModeConfiguration: Configuration, environment: Environment)
  extends AppConfig(runModeConfiguration, environment) {
  private val defaultSystemDate: Instant = LocalDate.parse("2019-02-03").atStartOfDay().toInstant(ZoneOffset.UTC)
  private val defaultZoneId = ZoneId.systemDefault()
  override lazy val clock: Clock = Clock.fixed(defaultSystemDate, defaultZoneId)
}
