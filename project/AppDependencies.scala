import play.sbt.PlayImport._
import sbt._
import play.core.PlayVersion.current

object AppDependencies {
  private lazy val mongoHmrcVersion = "0.71.0"
  val AkkaVersion                   = "2.6.20"
  private val silencerVersion       = "1.7.9"

  private val silencerDependencies: Seq[ModuleID] = Seq(
    compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
  )

  lazy val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-28"   % "7.2.0",
    "uk.gov.hmrc"                  %% "crypto"                      % "7.1.0",
    "uk.gov.hmrc"                  %% "play-json-union-formatter"   % "1.15.0-play-28",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-28"          % mongoHmrcVersion,
    "com.lightbend.akka"           %% "akka-stream-alpakka-mongodb" % "3.0.4",
    "com.typesafe.akka"            %% "akka-stream"                 % AkkaVersion,
    "com.typesafe.akka"            %% "akka-actor-typed"            % AkkaVersion,
    "com.typesafe.akka"            %% "akka-slf4j"                  % AkkaVersion,
    "com.typesafe.akka"            %% "akka-serialization-jackson"  % AkkaVersion,
    "com.typesafe.play"            %% "play-json"                   % "2.9.3",
    "org.typelevel"                %% "cats-core"                   % "2.8.0",
    "com.github.pathikrit"         %% "better-files"                % "3.9.1",
    "org.quartz-scheduler"         % "quartz"                       % "2.3.2",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"        % "2.13.4",
    ws
  )

  val test: Seq[ModuleID] = Seq(
    "com.github.tomakehurst" % "wiremock"                  % "2.33.2",
    "com.typesafe.play"      %% "play-test"                % current,
    "org.mockito"            % "mockito-core"              % "4.8.0",
    "org.jsoup"              % "jsoup"                     % "1.15.3",
    "org.scalatest"          %% "scalatest"                % "3.2.13",
    "org.scalatestplus.play" %% "scalatestplus-play"       % "5.1.0",
    "org.scalatestplus"      %% "mockito-3-4"              % "3.2.10.0",
    "com.vladsch.flexmark"   % "flexmark-all"              % "0.62.2",
    "org.scalacheck"         %% "scalacheck"               % "1.16.0",
    "uk.gov.hmrc"            %% "http-verbs-play-28"       % "14.5.0",
    "uk.gov.hmrc"            %% "service-integration-test" % "1.3.0-play-28",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28"  % mongoHmrcVersion,
    "org.scalaj"             %% "scalaj-http"              % "2.4.2"
  ).map(_ % "test, it")

  def apply(): Seq[ModuleID] = compile ++ silencerDependencies ++ test
}
