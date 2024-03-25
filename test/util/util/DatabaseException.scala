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

package util

import com.mongodb.WriteError
import org.bson.BsonDocument
import org.mongodb.scala.{MongoWriteException, ServerAddress}

object DatabaseException {

  def exception(code: Int, message: String, bsonDocument: Option[BsonDocument] = None): MongoWriteException =
    new MongoWriteException(new WriteError(code, message, bsonDocument.getOrElse(new BsonDocument())), ServerAddress())

}
