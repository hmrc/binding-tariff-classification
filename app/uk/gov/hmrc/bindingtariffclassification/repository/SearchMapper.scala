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

import java.time.Instant
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{Json, _}
import uk.gov.hmrc.bindingtariffclassification.config.AppConfig
import uk.gov.hmrc.bindingtariffclassification.model.ApplicationType.ApplicationType
import uk.gov.hmrc.bindingtariffclassification.model.MongoFormatters.*
import uk.gov.hmrc.bindingtariffclassification.model.PseudoCaseStatus.PseudoCaseStatus
import uk.gov.hmrc.bindingtariffclassification.model.{CaseFilter, CaseSort, CaseStatus, PseudoCaseStatus}
import uk.gov.hmrc.bindingtariffclassification.sort.CaseSortField._

import java.util.regex.Pattern

@Singleton
class SearchMapper @Inject() (appConfig: AppConfig) extends Mapper {

  def sortBy(sort: CaseSort): JsObject = {
    val fields = sort.field.toSeq
    println(">>> Fields" + fields)

    val sortMap = fields.flatMap { field =>
      val directionValue = toMongoDirection(field, sort.direction.id)
      val mainField = toMongoField(field) -> Json.toJson(directionValue)

      if (field == COMMODITY_CODE) {
        Seq(
          mainField,
          toMongoField(APPLICATION_TYPE) -> Json.toJson(directionValue),
          toMongoField(CREATED_DATE) -> Json.toJson(directionValue),
        )
      } else {
        Seq(mainField)
      }
    }
    JsObject(sortMap)
  }

  def filterBy(filter: CaseFilter): JsObject = {

    val caseDetailsVal = filter.caseDetails.toSeq
    val caseSourceVal = filter.caseSource.toSeq
    val decisionDetailVal = filter.decisionDetails.toSeq
    val activeTextValues = caseDetailsVal ++ caseSourceVal ++ decisionDetailVal

    val textSearchFilter = if (activeTextValues.nonEmpty) {
      Some("$text" -> Json.obj("$search" -> activeTextValues.mkString(" ")))
    } else {
      None
    }

    val params: Seq[(String, JsValue)] = Seq(
      filter.reference.map(ref => "reference" -> inArray[String](ref)),
      filter.applicationType.map(types => "application.type" -> inArray[String](types.map(_.toString))),
      filter.queueId
        .filterNot(ids => ids.contains("some") && ids.contains("none"))
        .map(ids => "queueId" -> inArrayOrNone[String](ids)),
      filter.assigneeId.map(id => "assignee.id" -> mappingNoneOrSome(id)),

      if (filter.advanceSearch.getOrElse(false)) {
        Seq(
          filter.caseDetails.map(value =>
            "$or" -> JsArray(Seq(
              Json.obj("application.goodName" -> containsGuard(value)),
              Json.obj("application.summary" -> containsGuard(value)),
              Json.obj("application.detailedDescription" -> containsGuard(value))
            ))
          ),
          filter.caseSource.map(value =>
            "$or" -> JsArray(Seq(
              Json.obj("application.holder.businessName" -> containsGuard(value)),
              Json.obj("application.traderName" -> containsGuard(value))
            ))
          ),
          filter.decisionDetails.map(value =>
            "$or" -> JsArray(Seq(
              Json.obj("decision.goodsDescription" -> containsGuard(value)),
              Json.obj("decision.methodCommercialDenomination" -> containsGuard(value)),
              Json.obj("decision.justification" -> containsGuard(value))
            ))
          )
        ).flatten
      } else {
        Seq.empty
      },

      textSearchFilter,

      filter.minDecisionStart.map(start => "decision.effectiveStartDate" -> greaterThan(start)(formatInstant)),
      filter.minDecisionEnd.map(end => "decision.effectiveEndDate" -> greaterThan(end)(formatInstant)),
      filter.commodityCode.map(code => "decision.bindingCommodityCode" -> numberStartingWith(code)),

      filter.eori.map(e =>
        "$or" -> JsArray(Seq(
          Json.obj("application.holder.eori" -> JsString(e)),
          Json.obj("application.agent.eoriDetails.eori" -> JsString(e))
        ))
      ),

      filter.keywords.map(k => "keywords" -> containsAll(k)),
      filter.statuses.map(s => filteringByStatus(s, filter.advanceSearch)),
      filter.migrated.map(showMigrated => if (showMigrated) exists("dateOfExtract") else notExists("dateOfExtract"))
    ).flatten

    val query: Map[String, JsValue] = params
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2))
      .map {
        case (key: String, value: Seq[JsValue]) if value.size == 1 => key -> value.head
        case (key, value: Seq[JsValue]) => "$and" -> JsArray(value.map(v => Json.obj(key -> v)))
      }
      .toMap

    JsObject(query)
  }

  private def containsGuard(value: String): JsValue = Json.obj(
    "$regex" -> value,
    "$options" -> "i"
  )

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
      case _ if values.iterator.contains("some") =>
        Json.obj("$ne" -> JsNull)
      case _ if values.iterator.contains("none") =>
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

  private def regexFilter(reg: String): (String, JsValueWrapper) = "$regex" -> reg

  private def filteringByApplicationType(search: Set[ApplicationType]): (String, JsValue) =
    "application.type" -> inArray(search)

  private def filteringByStatus(search: Set[PseudoCaseStatus], isAdvanceSearch: Option[Boolean]): (String, JsValue) = {
    val concreteStatuses: Set[String] = CaseStatus.values.map(_.toString)

    search.partition(status => concreteStatuses.contains(status.toString)) match {
      case (concrete: Set[PseudoCaseStatus], pseudo: Set[PseudoCaseStatus]) if pseudo.isEmpty =>
        val completedOnlySet = concrete.filter(_ == PseudoCaseStatus.COMPLETED)
        val otherSet         = concrete -- completedOnlySet
        if (isAdvanceSearch.getOrElse(false) && completedOnlySet.nonEmpty) {
          val filters: Seq[JsObject] = Seq(
            Some(
              Json.obj(
                "$or" -> Json.arr(
                  Json.obj(
                    "status"                    -> Json.toJson(PseudoCaseStatus.COMPLETED),
                    "decision.effectiveEndDate" -> greaterThan(Instant.now(appConfig.clock))(formatInstant)
                  ),
                  Json.obj(
                    "status"                    -> Json.toJson(PseudoCaseStatus.COMPLETED),
                    "decision.effectiveEndDate" -> JsNull
                  )
                )
              )
            ),
            if (otherSet.nonEmpty) Some(Json.obj("status" -> inArray(otherSet))) else None
          ).flatten

          either(filters)
        } else {
          "status" -> inArray(concrete)
        }

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
