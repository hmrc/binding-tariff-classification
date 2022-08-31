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

package uk.gov.hmrc.bindingtariffclassification.model.reporting

import java.net.URLDecoder
import java.time.Instant

import cats.data.NonEmptySeq
import uk.gov.hmrc.bindingtariffclassification.base.BaseSpec
import uk.gov.hmrc.bindingtariffclassification.sort.SortDirection
import uk.gov.hmrc.bindingtariffclassification.model.{ApplicationType, LiabilityStatus, PseudoCaseStatus}

class ReportSpec extends BaseSpec {
  "CaseReport" should {
    "bind from query string" in {
      val params1 = Map[String, Seq[String]](
        "sort_by"          -> Seq("count"),
        "sort_order"       -> Seq("desc"),
        "case_type"        -> Seq("BTI", "CORRESPONDENCE"),
        "status"           -> Seq("LIVE", "REFERRED"),
        "liability_status" -> Seq(),
        "team"             -> Seq("1", "3"),
        "min_date"         -> Seq("2020-03-21T12:03:15.000Z"),
        "max_date"         -> Seq("2021-03-21T12:03:15.000Z"),
        "fields"           -> Seq("reference", "status", "assigned_user", "source")
      )

      CaseReport.caseReportQueryStringBindable.bind("", params1) shouldBe Some(
        Right(
          CaseReport(
            sortBy            = ReportField.Count,
            sortOrder         = SortDirection.DESCENDING,
            caseTypes         = Set(ApplicationType.BTI, ApplicationType.CORRESPONDENCE),
            statuses          = Set(PseudoCaseStatus.LIVE, PseudoCaseStatus.REFERRED),
            liabilityStatuses = Set(),
            teams             = Set("1", "3"),
            dateRange = InstantRange(
              Instant.parse("2020-03-21T12:03:15.000Z"),
              Instant.parse("2021-03-21T12:03:15.000Z")
            ),
            fields = NonEmptySeq.of(ReportField.Reference, ReportField.Status, ReportField.User, ReportField.CaseSource)
          )
        )
      )

      val params2 = Map[String, Seq[String]](
        "sort_by"          -> Seq("date_created"),
        "sort_order"       -> Seq("asc"),
        "case_type"        -> Seq("MISCELLANEOUS", "CORRESPONDENCE"),
        "status"           -> Seq("COMPLETED", "REJECTED"),
        "liability_status" -> Seq("NON_LIVE"),
        "team"             -> Seq("4", "5"),
        "fields"           -> Seq("reference", "status", "elapsed_days", "total_days")
      )

      CaseReport.caseReportQueryStringBindable.bind("", params2) shouldBe Some(
        Right(
          CaseReport(
            sortBy            = ReportField.DateCreated,
            sortOrder         = SortDirection.ASCENDING,
            caseTypes         = Set(ApplicationType.MISCELLANEOUS, ApplicationType.CORRESPONDENCE),
            statuses          = Set(PseudoCaseStatus.COMPLETED, PseudoCaseStatus.REJECTED),
            liabilityStatuses = Set(LiabilityStatus.NON_LIVE),
            teams             = Set("4", "5"),
            fields =
              NonEmptySeq.of(ReportField.Reference, ReportField.Status, ReportField.ElapsedDays, ReportField.TotalDays)
          )
        )
      )

      val minParams = Map[String, Seq[String]](
        "fields" -> Seq("reference", "status", "liability_status", "elapsed_days", "total_days")
      )

      CaseReport.caseReportQueryStringBindable.bind("", minParams) shouldBe Some(
        Right(
          CaseReport(
            sortBy = ReportField.Reference,
            fields = NonEmptySeq.of(
              ReportField.Reference,
              ReportField.Status,
              ReportField.LiabilityStatus,
              ReportField.ElapsedDays,
              ReportField.TotalDays
            )
          )
        )
      )
    }

    "unbind to query string" in {
      URLDecoder.decode(
        CaseReport.caseReportQueryStringBindable.unbind(
          "",
          CaseReport(
            fields            = NonEmptySeq.one(ReportField.Reference),
            sortBy            = ReportField.Count,
            sortOrder         = SortDirection.DESCENDING,
            caseTypes         = Set(ApplicationType.BTI, ApplicationType.CORRESPONDENCE),
            statuses          = Set(PseudoCaseStatus.LIVE, PseudoCaseStatus.NEW),
            liabilityStatuses = Set(LiabilityStatus.NON_LIVE),
            teams             = Set("1", "3"),
            dateRange = InstantRange(
              Instant.parse("2020-03-21T12:03:15.000Z"),
              Instant.parse("2021-03-21T12:03:15.000Z")
            )
          )
        ),
        "UTF-8"
      ) shouldBe (
        "sort_by=count" +
          "&sort_order=desc" +
          "&case_type=BTI,CORRESPONDENCE" +
          "&status=LIVE,NEW" +
          "&liability_status=NON_LIVE" +
          "&team=1,3" +
          "&min_date=2020-03-21T12:03:15Z" +
          "&max_date=2021-03-21T12:03:15Z" +
          "&fields=reference"
      )

      URLDecoder.decode(
        CaseReport.caseReportQueryStringBindable.unbind(
          "",
          CaseReport(
            sortBy            = ReportField.DateCreated,
            sortOrder         = SortDirection.ASCENDING,
            caseTypes         = Set(ApplicationType.MISCELLANEOUS, ApplicationType.CORRESPONDENCE),
            statuses          = Set(PseudoCaseStatus.COMPLETED, PseudoCaseStatus.REJECTED),
            liabilityStatuses = Set(LiabilityStatus.NON_LIVE),
            teams             = Set("4", "5"),
            fields = NonEmptySeq.of(
              ReportField.Reference,
              ReportField.CaseSource,
              ReportField.Status,
              ReportField.ElapsedDays,
              ReportField.TotalDays
            )
          )
        ),
        "UTF-8"
      ) shouldBe (
        "sort_by=date_created" +
          "&sort_order=asc" +
          "&case_type=MISCELLANEOUS,CORRESPONDENCE" +
          "&status=COMPLETED,REJECTED" +
          "&liability_status=NON_LIVE" +
          "&team=4,5" +
          "&min_date=-1000000000-01-01T00:00:00Z" +
          "&max_date=+1000000000-12-31T23:59:59.999999999Z" +
          "&fields=reference,source,status,elapsed_days,total_days"
      )
    }
  }

  "SummaryReport" should {
    "bind from query string" in {
      val params1 = Map[String, Seq[String]](
        "group_by"   -> Seq("status"),
        "sort_by"    -> Seq("count"),
        "sort_order" -> Seq("desc"),
        "case_type"  -> Seq("BTI", "CORRESPONDENCE"),
        "status"     -> Seq("LIVE", "REFERRED"),
        "team"       -> Seq("1", "3"),
        "min_date"   -> Seq("2020-03-21T12:03:15.000Z"),
        "max_date"   -> Seq("2021-03-21T12:03:15.000Z"),
        "max_fields" -> Seq("total_days")
      )

      SummaryReport.summaryReportQueryStringBindable.bind("", params1) shouldBe Some(
        Right(
          SummaryReport(
            groupBy   = NonEmptySeq.one(ReportField.Status),
            sortBy    = ReportField.Count,
            sortOrder = SortDirection.DESCENDING,
            caseTypes = Set(ApplicationType.BTI, ApplicationType.CORRESPONDENCE),
            statuses  = Set(PseudoCaseStatus.LIVE, PseudoCaseStatus.REFERRED),
            teams     = Set("1", "3"),
            maxFields = Seq(ReportField.TotalDays),
            dateRange = InstantRange(
              Instant.parse("2020-03-21T12:03:15.000Z"),
              Instant.parse("2021-03-21T12:03:15.000Z")
            )
          )
        )
      )

      val params2 = Map[String, Seq[String]](
        "group_by"   -> Seq("assigned_user"),
        "sort_by"    -> Seq("date_created"),
        "sort_order" -> Seq("asc"),
        "case_type"  -> Seq("MISCELLANEOUS", "CORRESPONDENCE"),
        "status"     -> Seq("COMPLETED", "REJECTED"),
        "team"       -> Seq("4", "5"),
        "max_fields" -> Seq("elapsed_days")
      )

      SummaryReport.summaryReportQueryStringBindable.bind("", params2) shouldBe Some(
        Right(
          SummaryReport(
            groupBy   = NonEmptySeq.one(ReportField.User),
            sortBy    = ReportField.DateCreated,
            sortOrder = SortDirection.ASCENDING,
            caseTypes = Set(ApplicationType.MISCELLANEOUS, ApplicationType.CORRESPONDENCE),
            statuses  = Set(PseudoCaseStatus.COMPLETED, PseudoCaseStatus.REJECTED),
            teams     = Set("4", "5"),
            maxFields = Seq(ReportField.ElapsedDays)
          )
        )
      )

      val minParams = Map[String, Seq[String]](
        "group_by" -> Seq("assigned_user"),
        "sort_by"  -> Seq("assigned_user")
      )

      SummaryReport.summaryReportQueryStringBindable.bind("", minParams) shouldBe Some(
        Right(
          SummaryReport(
            groupBy = NonEmptySeq.one(ReportField.User),
            sortBy  = ReportField.User
          )
        )
      )
    }

    "unbind to query string" in {
      URLDecoder.decode(
        SummaryReport.summaryReportQueryStringBindable.unbind(
          "",
          SummaryReport(
            groupBy           = NonEmptySeq.one(ReportField.Status),
            sortBy            = ReportField.Count,
            sortOrder         = SortDirection.DESCENDING,
            caseTypes         = Set(ApplicationType.BTI, ApplicationType.CORRESPONDENCE),
            statuses          = Set(PseudoCaseStatus.LIVE, PseudoCaseStatus.REFERRED),
            liabilityStatuses = Set(LiabilityStatus.NON_LIVE),
            teams             = Set("1", "3"),
            maxFields         = Seq(ReportField.ElapsedDays),
            dateRange = InstantRange(
              Instant.parse("2020-03-21T12:03:15.000Z"),
              Instant.parse("2021-03-21T12:03:15.000Z")
            )
          )
        ),
        "UTF-8"
      ) shouldBe (
        "group_by=status" +
          "&sort_by=count" +
          "&sort_order=desc" +
          "&case_type=BTI,CORRESPONDENCE" +
          "&status=LIVE,REFERRED" +
          "&liability_status=NON_LIVE" +
          "&team=1,3" +
          "&min_date=2020-03-21T12:03:15Z" +
          "&max_date=2021-03-21T12:03:15Z" +
          "&max_fields=elapsed_days" +
          "&include_cases=false"
      )

      URLDecoder.decode(
        SummaryReport.summaryReportQueryStringBindable.unbind(
          "",
          SummaryReport(
            groupBy           = NonEmptySeq.one(ReportField.User),
            sortBy            = ReportField.DateCreated,
            sortOrder         = SortDirection.ASCENDING,
            caseTypes         = Set(ApplicationType.MISCELLANEOUS, ApplicationType.CORRESPONDENCE),
            statuses          = Set(PseudoCaseStatus.COMPLETED, PseudoCaseStatus.REJECTED),
            liabilityStatuses = Set(LiabilityStatus.NON_LIVE),
            teams             = Set("4", "5"),
            maxFields         = Seq(ReportField.TotalDays)
          )
        ),
        "UTF-8"
      ) shouldBe (
        "group_by=assigned_user" +
          "&sort_by=date_created" +
          "&sort_order=asc" +
          "&case_type=MISCELLANEOUS,CORRESPONDENCE" +
          "&status=COMPLETED,REJECTED" +
          "&liability_status=NON_LIVE" +
          "&team=4,5" +
          "&min_date=-1000000000-01-01T00:00:00Z" +
          "&max_date=+1000000000-12-31T23:59:59.999999999Z" +
          "&max_fields=total_days" +
          "&include_cases=false"
      )
    }
  }

  "QueueReport" should {
    "bind from query string" in {
      val params1 = Map[String, Seq[String]](
        "sort_by"       -> Seq("count"),
        "sort_order"    -> Seq("desc"),
        "case_type"     -> Seq("BTI", "CORRESPONDENCE"),
        "status"        -> Seq("LIVE", "REFERRED"),
        "team"          -> Seq("1", "3"),
        "assigned_user" -> Seq("1"),
        "min_date"      -> Seq("2020-03-21T12:03:15.000Z"),
        "max_date"      -> Seq("2021-03-21T12:03:15.000Z")
      )

      QueueReport.queueReportQueryStringBindable.bind("", params1) shouldBe Some(
        Right(
          QueueReport(
            sortBy    = ReportField.Count,
            sortOrder = SortDirection.DESCENDING,
            caseTypes = Set(ApplicationType.BTI, ApplicationType.CORRESPONDENCE),
            statuses  = Set(PseudoCaseStatus.LIVE, PseudoCaseStatus.REFERRED),
            teams     = Set("1", "3"),
            assignee  = Some("1"),
            dateRange = InstantRange(
              Instant.parse("2020-03-21T12:03:15.000Z"),
              Instant.parse("2021-03-21T12:03:15.000Z")
            )
          )
        )
      )

      val params2 = Map[String, Seq[String]](
        "sort_by"          -> Seq("date_created"),
        "sort_order"       -> Seq("asc"),
        "case_type"        -> Seq("MISCELLANEOUS", "CORRESPONDENCE"),
        "status"           -> Seq("COMPLETED", "REJECTED"),
        "liability_status" -> Seq("NON_LIVE"),
        "team"             -> Seq("4", "5")
      )

      QueueReport.queueReportQueryStringBindable.bind("", params2) shouldBe Some(
        Right(
          QueueReport(
            sortBy            = ReportField.DateCreated,
            sortOrder         = SortDirection.ASCENDING,
            caseTypes         = Set(ApplicationType.MISCELLANEOUS, ApplicationType.CORRESPONDENCE),
            statuses          = Set(PseudoCaseStatus.COMPLETED, PseudoCaseStatus.REJECTED),
            liabilityStatuses = Set(LiabilityStatus.NON_LIVE),
            teams             = Set("4", "5")
          )
        )
      )

      QueueReport.queueReportQueryStringBindable.bind("", Map.empty) shouldBe Some(Right(QueueReport()))
    }

    "unbind to query string" in {
      URLDecoder.decode(
        QueueReport.queueReportQueryStringBindable.unbind(
          "",
          QueueReport(
            sortBy            = ReportField.Count,
            sortOrder         = SortDirection.DESCENDING,
            caseTypes         = Set(ApplicationType.BTI, ApplicationType.CORRESPONDENCE),
            statuses          = Set(PseudoCaseStatus.LIVE, PseudoCaseStatus.NEW),
            liabilityStatuses = Set(LiabilityStatus.NON_LIVE),
            teams             = Set("1", "3"),
            dateRange = InstantRange(
              Instant.parse("2020-03-21T12:03:15.000Z"),
              Instant.parse("2021-03-21T12:03:15.000Z")
            )
          )
        ),
        "UTF-8"
      ) shouldBe (
        "sort_by=count" +
          "&sort_order=desc" +
          "&case_type=BTI,CORRESPONDENCE" +
          "&status=LIVE,NEW" +
          "&liability_status=NON_LIVE" +
          "&team=1,3" +
          "&min_date=2020-03-21T12:03:15Z" +
          "&max_date=2021-03-21T12:03:15Z"
      )

      URLDecoder.decode(
        QueueReport.queueReportQueryStringBindable.unbind(
          "",
          QueueReport(
            sortBy            = ReportField.DateCreated,
            sortOrder         = SortDirection.ASCENDING,
            caseTypes         = Set(ApplicationType.MISCELLANEOUS, ApplicationType.CORRESPONDENCE),
            statuses          = Set(PseudoCaseStatus.COMPLETED, PseudoCaseStatus.REJECTED),
            liabilityStatuses = Set(LiabilityStatus.NON_LIVE),
            teams             = Set("4", "5")
          )
        ),
        "UTF-8"
      ) shouldBe (
        "sort_by=date_created" +
          "&sort_order=asc" +
          "&case_type=MISCELLANEOUS,CORRESPONDENCE" +
          "&status=COMPLETED,REJECTED" +
          "&liability_status=NON_LIVE" +
          "&team=4,5" +
          "&min_date=-1000000000-01-01T00:00:00Z" +
          "&max_date=+1000000000-12-31T23:59:59.999999999Z"
      )
    }
  }
}
