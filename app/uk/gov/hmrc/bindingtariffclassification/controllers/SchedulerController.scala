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

package uk.gov.hmrc.bindingtariffclassification.controllers

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.scheduler.ScheduledJob

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class SchedulerController @Inject()(appConfig: AppConfig,
                                    scheduledJob: ScheduledJob) extends CommonController {

  lazy private val testModeFilter = TestMode.actionFilter(appConfig)

  def incrementElapsedDays(): Action[AnyContent] = testModeFilter.async { implicit request =>
    scheduledJob.execute() map (_ => Ok) recover recovery
  }

}