import sbt.Setting
import scoverage.ScoverageKeys.*

object CodeCoverageSettings {
  private val excludedPackages: Seq[String] = Seq(
    "prod.*;testOnlyDoNotUseInAppConf.*;app.*;.*(Routes).*;.*(JobFactory).*;.*Event.*;.*RepaymentClaim.*;.*Cancellation.*;.*CaseUpdate.*;.*ScheduledJob.*;.*$anon.*;.*Application.*",
    ".*\\$anonfun\\$.*",
    ".*\\$anon\\$.*",
    ".*\\$anon.*",
    ".*\\$.*\\$\\$.*" // Add this pattern to catch more anonymous function variants
  )

  private val settings: Seq[Setting[?]] = Seq(
    coverageExcludedFiles := excludedPackages.mkString(";"),
    coverageMinimumStmtTotal := 92,
    coverageFailOnMinimum := true,
    coverageHighlighting := true
  )

  def apply(): Seq[Setting[?]] = settings
}
