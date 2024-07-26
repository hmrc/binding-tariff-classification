/*
 * Copyright 2024 HM Revenue & Customs
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

import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import org.scalatest.Assertion
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec

import scala.jdk.CollectionConverters.{IterableHasAsScala, SetHasAsScala}

trait BaseMongoIndexSpec extends BaseSpec {

  protected implicit val ordering: Ordering[IndexModel] = Ordering.by { i: IndexModel => i.toString }

  protected def getIndexes(collection: MongoCollection[_]): Seq[IndexModel] =
    await(
      collection
        .listIndexes()
        .toFuture()
        .map(_.map { document =>
          val indexFields = document.get("key").map(_.asDocument().keySet().asScala).getOrElse(Set.empty[String]).toSeq
          val name        = document.getString("name")
          val isUnique    = document.getBoolean("unique", false)
          val sorting =
            document.get("key").map(_.asDocument().values().asScala.head.asInt32().getValue.toString).getOrElse("1")
          val indexes = if (sorting == "1") Indexes.ascending(indexFields: _*) else Indexes.descending(indexFields: _*)
          IndexModel(indexes, IndexOptions().name(name).unique(isUnique))
        })
    )

  protected def assertIndexes(expectedIndexes: Iterable[IndexModel], actualIndexes: Iterable[IndexModel]): Unit = {
    actualIndexes.size shouldBe expectedIndexes.size

    expectedIndexes
      .zip(actualIndexes)
      .foreach { indexTuple =>
        val expectedIndex = indexTuple._1
        val actualIndex   = indexTuple._2

        assertIndex(expectedIndex, actualIndex)
      }
  }

  private def assertIndex(expectedIndex: IndexModel, actualIndex: IndexModel): Assertion = {
    actualIndex.getKeys.toBsonDocument.keySet().asScala shouldBe expectedIndex.getKeys.toBsonDocument.keySet().asScala
    actualIndex.getKeys.toBsonDocument.toString         shouldBe expectedIndex.getKeys.toBsonDocument.toString

    actualIndex.getOptions.toString shouldBe expectedIndex.getOptions.toString
  }

}
