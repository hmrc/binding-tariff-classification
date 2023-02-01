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

package uk.gov.hmrc.bindingtariffclassification.repository

import java.time.Instant
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.JsValue
import play.api.libs.json.{Json, _}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.ApplicationType.ApplicationType
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.formatInstant
import uk.gov.hmrc.bindingtariffclassification.model.PseudoCaseStatus.PseudoCaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.{CaseFilter, CaseSort, CaseStatus, PseudoCaseStatus}
import uk.gov.hmrc.bindingtariffclassification.sort.CaseSortField._

@Singleton
class SearchMapper @Inject() (appConfig: AppConfig) extends Mapper {

  def sortBy(sort: CaseSort): JsObject =
    JsObject(
      sort.field.toSeq.map(field => (toMongoField(field), Json.toJson(toMongoDirection(field, sort.direction.id))))
    )

  def filterBy(filter: CaseFilter): JsObject = {
    val params = Seq[Option[(String, JsValue)]](
      filter.reference.map("reference" -> inArray[String](_)),
      filter.applicationType.map(filteringByApplicationType),
      filter.queueId
        .filterNot(ids => ids.contains("some") && ids.contains("none"))
        .map("queueId"                    -> inArrayOrNone[String](_)),
      filter.assigneeId.map("assignee.id" -> mappingNoneOrSome(_)),
      filter.caseDetails.map(details =>
        either(
          "application.goodName"            -> contains(details),
          "application.summary"             -> contains(details),
          "application.detailedDescription" -> contains(details),
          "application.name"                -> contains(details)
        )
      ),
      filter.caseSource.map(source =>
        either(
          "application.holder.businessName"   -> contains(source),
          "application.traderName"            -> contains(source),
          "application.correspondenceStarter" -> contains(source),
          "application.contactName"           -> contains(source)
        )
      ),
      filter.minDecisionStart.map("decision.effectiveStartDate" -> greaterThan(_)(formatInstant)),
      filter.minDecisionEnd.map("decision.effectiveEndDate"     -> greaterThan(_)(formatInstant)),
      filter.commodityCode.map("decision.bindingCommodityCode"  -> numberStartingWith(_)),
      filter.decisionDetails.map(desc =>
        either(
          "decision.goodsDescription"             -> contains(desc),
          "decision.methodCommercialDenomination" -> contains(desc),
          "decision.justification"                -> contains(desc)
        )
      ),
      filter.eori.map(e =>
        either(
          "application.holder.eori"            -> JsString(e),
          "application.agent.eoriDetails.eori" -> JsString(e)
        )
      ),
      filter.keywords.map("keywords" -> containsAll(_)),
      filter.statuses.map(filteringByStatus),
      filter.migrated.map(showMigrated => if (showMigrated) exists("dateOfExtract") else notExists("dateOfExtract"))
    ).filter(_.isDefined).map(_.get)

    val query: Map[String, JsValue] = params
      .groupBy(_._1) // Group by Key
      .view
      .mapValues(_.map(_._2)) // Map the values from Seq(key -> value) to Seq(value)
      .map({
        // If there is only one entry in params with a specific key, map it how we usually would: (key -> value)
        case (key: String, value: Seq[JsValue]) if value.size == 1 => key -> value.head

        // If there is multiple entries in params with a specific key, wrap them in an $and
        case (key, value: Seq[JsValue]) => "$and" -> JsArray(value.map(value => Json.obj(key -> value)))
      })
      .toMap

    JsObject(query)
  }

  private def either(conditions: Iterable[JsObject]): (String, JsArray) = "$or" -> JsArray(conditions.toSeq)

  private def either(conditions: (String, JsValue)*): (String, JsArray) = {
    val objects: Seq[JsObject] = conditions.map(element => Json.obj(element._1 -> element._2))
    either(objects)
  }

  private def notExists(field: String): (String, JsValue) =
    field -> JsNull

  private def exists(field: String): (String, JsObject) =
    field -> Json.obj("$exists" -> JsTrue)

  private def containsAll(s: Set[String]): JsObject = Json.obj(
    "$all" -> s
  )

  private def greaterThan[T](value: T)(implicit writes: Writes[T]): JsObject = Json.obj(
    "$gte" -> value
  )

  private def lessThan[T](value: T)(implicit writes: Writes[T]): JsObject = Json.obj(
    "$lte" -> value
  )

  private def inArray[T](values: IterableOnce[T])(implicit writes: Writes[T]): JsObject =
    JsObject(Map("$in" -> JsArray(values.iterator.toSeq.map(writes.writes))))

  private def inArrayOrNone[T](values: IterableOnce[T])(implicit writes: Writes[T]): JsObject =
    values match {
      case _ if values.iterator.exists(_ == "some") =>
        Json.obj("$ne" -> JsNull)
      case _ if values.iterator.exists(_ == "none") =>
        JsObject(
          Map(
            "$in" -> JsArray(JsNull :: values.iterator.toList.filterNot(_ == "none").map(writes.writes))
          )
        )
      case _ =>
        inArray(values)
    }

  private def mappingNoneOrSome: String => JsValue = {
    case "none" => JsNull
    case "some" => Json.obj("$ne" -> JsNull)
    case v      => JsString(v)
  }

  private def numberStartingWith(value: String): JsObject = Json.obj(
    regexFilter(s"^$value\\d*")
  )

  private def contains(value: String): JsObject = Json.obj(
    regexFilter(s".*$value.*"),
    caseInsensitiveFilter
  )

  private def regexFilter(reg: String): (String, JsValueWrapper) = "$regex" -> reg

  private lazy val caseInsensitiveFilter: (String, JsValueWrapper) = "$options" -> "i"

  private def filteringByApplicationType(search: Set[ApplicationType]): (String, JsValue) =
    "application.type" -> inArray(search)

  private def filteringByStatus(search: Set[PseudoCaseStatus]): (String, JsValue) = {
    val concreteStatuses: Set[String] = CaseStatus.values.map(_.toString)

    search.partition(status => concreteStatuses.contains(status.toString)) match {
      case (concrete: Set[PseudoCaseStatus], pseudo: Set[PseudoCaseStatus]) if pseudo.isEmpty =>
        "status" -> inArray(concrete)

      case (concrete: Set[PseudoCaseStatus], pseudo: Set[PseudoCaseStatus]) if concrete.isEmpty =>
        val pseudoFilters: Set[JsObject] = pseudo.map(pseudoStatus).filter(_.isDefined).map(_.get)
        either(pseudoFilters)

      case (concrete: Set[PseudoCaseStatus], pseudo: Set[PseudoCaseStatus]) =>
        val pseudoFilters: Set[JsObject] = pseudo.map(pseudoStatus).filter(_.isDefined).map(_.get)
        either(pseudoFilters + JsObject(Seq("status" -> inArray(concrete))))
    }
  }

  private def pseudoStatus(status: PseudoCaseStatus): Option[JsObject] =
    status match {
      case PseudoCaseStatus.LIVE =>
        Some(
          JsObject(
            Seq(
              "status"                    -> Json.toJson(PseudoCaseStatus.COMPLETED),
              "decision.effectiveEndDate" -> greaterThan(Instant.now(appConfig.clock))(formatInstant)
            )
          )
        )

      case PseudoCaseStatus.EXPIRED =>
        Some(
          JsObject(
            Seq(
              "status"                    -> Json.toJson(PseudoCaseStatus.COMPLETED),
              "decision.effectiveEndDate" -> lessThan(Instant.now(appConfig.clock))(formatInstant)
            )
          )
        )

      case _ => None
    }

  private def toMongoField(sort: CaseSortField): String =
    sort match {
      case REFERENCE           => "reference"
      case DAYS_ELAPSED        => "daysElapsed"
      case COMMODITY_CODE      => "decision.bindingCommodityCode"
      case CREATED_DATE        => "createdDate"
      case DECISION_START_DATE => "decision.effectiveStartDate"
      case APPLICATION_STATUS  => "application.status"
      case APPLICATION_TYPE    => "application.type"
      case STATUS              => "status"
      case s                   => throw new IllegalArgumentException(s"cannot sort by field: $s")
    }

  private def toMongoDirection(sort: CaseSortField, default: Int): Int =
    sort match {
      case APPLICATION_STATUS => 1
      case _                  => default
    }

}
