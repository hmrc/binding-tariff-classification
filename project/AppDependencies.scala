import play.sbt.PlayImport.*
import sbt.*

object AppDependencies {
  private lazy val mongoHmrcVersion = "1.5.0"
  private lazy val akkaVersion      = "2.6.20"
  private lazy val bootstrapVersion = "7.20.0"

  lazy val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-28"   % bootstrapVersion,
    "uk.gov.hmrc"                  %% "play-json-union-formatter"   % "1.18.0-play-28",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-28"          % mongoHmrcVersion,
    "com.lightbend.akka"           %% "akka-stream-alpakka-mongodb" % "4.0.0",
    "com.typesafe.akka"            %% "akka-stream"                 % akkaVersion,
    "com.typesafe.akka"            %% "akka-actor-typed"            % akkaVersion,
    "com.typesafe.akka"            %% "akka-slf4j"                  % akkaVersion,
    "com.typesafe.akka"            %% "akka-serialization-jackson"  % akkaVersion,
    "com.typesafe.play"            %% "play-json"                   % "2.9.4",
    "org.typelevel"                %% "cats-core"                   % "2.9.0",
    "com.github.pathikrit"         %% "better-files"                % "3.9.2",
    "org.quartz-scheduler"         % "quartz"                       % "2.3.2",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"        % "2.15.2",
    ws
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"  % bootstrapVersion,
    "com.github.tomakehurst" % "wiremock"                 % "2.33.2",
    "org.scalatest"          %% "scalatest"               % "3.2.16",
    "org.scalatestplus"      %% "mockito-4-11"            % "3.2.16.0",
    "com.vladsch.flexmark"   % "flexmark-all"             % "0.64.8",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % mongoHmrcVersion,
    "org.scalaj"             %% "scalaj-http"             % "2.4.2"
  ).map(_ % "test, it")

  def apply(): Seq[ModuleID] = compile ++ test
}
