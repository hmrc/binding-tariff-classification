/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.bindingtariffclassification.repository

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

class KeywordCountCacheSpec extends AnyWordSpec with Matchers with ScalaFutures {

  "KeywordCountCache" should {

    "cache the loaded value" in {
      val cache = KeywordCountCache()

      val loaderCalls = new AtomicInteger(0)

      def loader: Future[Long] =
        Future.successful {
          loaderCalls.incrementAndGet()
          42L
        }

      cache.getOrUpdate(loader).futureValue shouldBe 42L
      cache.getOrUpdate(loader).futureValue shouldBe 42L
      cache.getOrUpdate(loader).futureValue shouldBe 42L

      loaderCalls.get() shouldBe 1
    }

    "return the cached value even if a later loader would return something different" in {
      val cache = new KeywordCountCache()

      cache.getOrUpdate(Future.successful(42L)).futureValue shouldBe 42L

      cache.getOrUpdate(Future.successful(999L)).futureValue shouldBe 42L
    }
  }
}
