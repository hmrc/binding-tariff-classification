import play.sbt.PlayImport._
import sbt._
import play.core.PlayVersion.current

object AppDependencies {

  lazy val compile = Seq(
    "uk.gov.hmrc"          %% "bootstrap-backend-play-27"     % "5.3.0",
    "uk.gov.hmrc"          %% "crypto"                        % "6.0.0",
    "uk.gov.hmrc"          %% "play-json-union-formatter"     % "1.11.0",
    "uk.gov.hmrc"          %% "simple-reactivemongo"          % "7.31.0-play-27",
    "uk.gov.hmrc"          %% "mongo-lock"                    % "7.0.0-play-27",
    "org.reactivemongo"    %% "reactivemongo-akkastream"      % "0.18.8",
    "com.typesafe.play"    %% "play-json"                     % "2.9.2",
    "org.typelevel"        %% "cats-core"                     % "2.6.1",
    "com.github.pathikrit" %% "better-files"                  % "3.9.1",
    "org.quartz-scheduler" % "quartz"                         % "2.3.2",
    ws
  )

  val scope = "test, it"

  val jettyVersion = "9.4.27.v20200227"

  val test = Seq(
    "com.github.tomakehurst" % "wiremock"                  % "2.27.2"         % scope,
    "com.typesafe.play"      %% "play-test"                % current          % scope,
    "org.mockito"            % "mockito-core"              % "3.11.1"         % scope,
    "org.jsoup"              % "jsoup"                     % "1.13.1"         % scope,
    "org.pegdown"            % "pegdown"                   % "1.6.0"          % scope,
    "org.scalatest"          %% "scalatest"                % "3.0.9"          % scope,
    "org.scalatestplus.play" %% "scalatestplus-play"       % "4.0.3"          % scope,
    "org.scalacheck"         %% "scalacheck"               % "1.15.4"         % scope,
    "uk.gov.hmrc"            %% "http-verbs-test"          % "1.8.0-play-27"  % scope,
    "uk.gov.hmrc"            %% "service-integration-test" % "1.1.0-play-27"  % scope,
    "uk.gov.hmrc"            %% "reactivemongo-test"       % "4.22.0-play-27" % scope,
    "org.scalaj"             %% "scalaj-http"              % "2.4.2"          % scope,
    //Need to peg this version for wiremock - try removing this on next lib upgrade
    "org.eclipse.jetty" % "jetty-server"  % jettyVersion % scope,
    "org.eclipse.jetty" % "jetty-servlet" % jettyVersion % scope
  )

}
