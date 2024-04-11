import play.sbt.PlayScala
import uk.gov.hmrc.DefaultBuildSettings.addTestReportOption
import uk.gov.hmrc.DefaultBuildSettings.itSettings

val appName = "binding-tariff-classification"

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "2.13.13"

lazy val microservice = (project in file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(CodeCoverageSettings.settings)
  .settings(
    name := appName,
    PlayKeys.playDefaultPort := 9580,
    libraryDependencies ++= AppDependencies(),
    Test / parallelExecution := false,
    scalacOptions += "-Wconf:src=routes/.*:s",
    scalacOptions ~= { opts =>
      opts.filterNot(
        Set(
          "-Xfatal-warnings",
          "-Ywarn-value-discard"
        )
      )
    }
  )
  .settings(inConfig(TemplateTest)(Defaults.testSettings))
  .settings(
    Test / unmanagedSourceDirectories := Seq(
      (Test / baseDirectory).value / "test/unit",
      (Test / baseDirectory).value / "test/util"
    ),
    addTestReportOption(Test, "test-reports")
  )

lazy val allPhases = "tt->test;test->test;test->compile;compile->compile"

lazy val TemplateTest = config("tt") extend Test

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(itSettings())

addCommandAlias("scalafmtAll", "all scalafmtSbt scalafmt Test/scalafmt it/Test/scalafmt")
addCommandAlias("scalastyleAll", "all scalastyle Test/scalastyle it/Test/scalastyle")
