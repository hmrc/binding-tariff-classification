import play.sbt.PlayScala
import uk.gov.hmrc.DefaultBuildSettings.itSettings

val appName = "binding-tariff-classification"

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.5.2"

lazy val microservice = (project in file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) // Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(CodeCoverageSettings())
  .settings(
    name := appName,
    PlayKeys.playDefaultPort := 9580,
    libraryDependencies ++= AppDependencies()
  )
  .settings(scalacSettings)

lazy val scalacSettings = Def.settings(
  scalacOptions ++= Seq("-Wconf:src=routes/.*:s", "-Wconf:msg=Flag.*repeatedly:s"),
  scalacOptions ~= { opts =>
    opts.filterNot(
      Set(
        "-Werror",
        "-Xfatal-warnings",
        "-Wnonunit-statement"
      )
    )
  }
)

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(itSettings())
  .settings(
    Def.settings(
      scalacOptions ~= { opts =>
        opts.filterNot(
          Set(
            "-Werror",
            "-Xfatal-warnings",
            "-Wnonunit-statement"
          )
        )
      }
    )
  )

addCommandAlias("scalafmtAll", "all scalafmtSbt scalafmt Test/scalafmt it/Test/scalafmt")
