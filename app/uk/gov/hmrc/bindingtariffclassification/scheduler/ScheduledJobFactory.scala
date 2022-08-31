/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.scheduler

import org.quartz
import org.quartz.Job
import org.quartz.spi.{JobFactory, TriggerFiredBundle}
import play.api.inject.Injector

import javax.inject.Inject

class ScheduledJobFactory @Inject() (injector: Injector) extends JobFactory {
  override def newJob(bundle: TriggerFiredBundle, scheduler: quartz.Scheduler): Job =
    injector.instanceOf(bundle.getJobDetail.getJobClass)
}
