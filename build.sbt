import play.sbt.PlayScala
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

val appName = "binding-tariff-classification"

lazy val plugins: Seq[Plugins] =
  Seq(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val microservice = (project in file("."))
  .enablePlugins(plugins: _*)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(playSettings: _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(majorVersion := 0)
  .settings(
    name := appName,
    scalaVersion := "2.12.12",
    targetJvm := "jvm-1.8",
    PlayKeys.playDefaultPort := 9580,
    libraryDependencies ++= (AppDependencies.compile ++ AppDependencies.test).map(_ withSources ()),
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    parallelExecution in Test := false,
    fork in Test := true,
    retrieveManaged := true,
    // Use the silencer plugin to suppress warnings from unused imports in compiled twirl templates
    scalacOptions += "-P:silencer:pathFilters=views;routes",
    scalacOptions ~= { opts =>
      opts.filterNot(
        Set(
          "-Xfatal-warnings",
          "-Ywarn-value-discard"
        )
      )
    },
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.1" cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % "1.7.1" % Provided cross CrossVersion.full
    )
  )
  .settings(inConfig(TemplateTest)(Defaults.testSettings): _*)
  .settings(
    unmanagedSourceDirectories in Test := Seq(
      (baseDirectory in Test).value / "test/unit",
      (baseDirectory in Test).value / "test/util"
    ),
    addTestReportOption(Test, "test-reports")
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(ScalafmtPlugin.scalafmtConfigSettings))
  .settings(inConfig(TemplateItTest)(Defaults.itSettings): _*)
  .settings(
    Keys.fork in IntegrationTest := true,
    unmanagedSourceDirectories in IntegrationTest := Seq(
      (baseDirectory in IntegrationTest).value / "test/it",
      (baseDirectory in Test).value / "test/util"
    ),
    addTestReportOption(IntegrationTest, "int-test-reports"),
    parallelExecution in IntegrationTest := false
  )
  .settings(resolvers += Resolver.jcenterRepo)

lazy val allPhases   = "tt->test;test->test;test->compile;compile->compile"
lazy val allItPhases = "tit->it;it->it;it->compile;compile->compile"

lazy val TemplateTest   = config("tt") extend Test
lazy val TemplateItTest = config("tit") extend IntegrationTest

// Coverage configuration
coverageMinimum := 94
coverageFailOnMinimum := true
coverageExcludedPackages := "<empty>;com.kenshoo.play.metrics.*;prod.*;testOnlyDoNotUseInAppConf.*;app.*;uk.gov.hmrc.BuildInfo"
