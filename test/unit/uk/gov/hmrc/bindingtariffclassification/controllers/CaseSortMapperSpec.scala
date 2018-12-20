package uk.gov.hmrc.bindingtariffclassification.controllers

import uk.gov.hmrc.bindingtariffclassification.model.sort.{CaseSort, CaseSortField, SortDirection}
import uk.gov.hmrc.play.test.UnitSpec

class CaseSortMapperSpec extends UnitSpec {

  private val field = "days-elapsed"
  private val directionAscending = "ascending"
  private val directionDescending = "descending"

  private val mapper = new CaseSortMapper()

  "CaseSortMapper" should {
    "Convert no params" in {
      mapper.from(None, None) shouldBe None
    }

    "Convert valid 'direction' only" in {
      mapper.from(None, Some(directionAscending)) shouldBe None
      mapper.from(None, Some(directionDescending)) shouldBe None
    }

    "Convert invalid 'direction' only" in {
      mapper.from(None, Some("x")) shouldBe None
    }

    "Convert valid 'field' only" in {
      mapper.from(Some(field), None) shouldBe Some(CaseSort(CaseSortField.DAYS_ELAPSED))
    }

    "Convert invalid 'field' only" in {
      mapper.from(Some("x"), None) shouldBe None
    }

    "Convert valid 'field' and 'direction'" in {
      mapper.from(
        Some(field),
        Some(directionAscending)
      ) shouldBe Some(CaseSort(CaseSortField.DAYS_ELAPSED, SortDirection.ASCENDING))

      mapper.from(
        Some(field),
        Some(directionDescending)
      ) shouldBe Some(CaseSort(CaseSortField.DAYS_ELAPSED, SortDirection.DESCENDING))
    }

    "Convert invalid 'field' and 'direction'" in {
      mapper.from(
        Some("x"),
        Some("x")
      ) shouldBe None
    }

    "Convert valid 'field' and invalid 'direction'" in {
      mapper.from(
        Some(field),
        Some("x")
      ) shouldBe Some(CaseSort(CaseSortField.DAYS_ELAPSED))
    }

    "Convert invalid 'field' and valid 'direction'" in {
      mapper.from(
        Some("x"),
        Some(directionAscending)
      ) shouldBe None

      mapper.from(
        Some("x"),
        Some(directionDescending)
      ) shouldBe None
    }
  }

}
