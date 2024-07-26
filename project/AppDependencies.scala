import sbt.*

object AppDependencies {
  private lazy val mongoHmrcVersion = "2.2.0"
  private lazy val bootstrapVersion = "9.1.0"
  private lazy val pekkoVersion     = "1.0.3"

  lazy val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30"   % bootstrapVersion,
    "uk.gov.hmrc"                  %% "play-json-union-formatter"   % "1.21.0",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"          % mongoHmrcVersion,
    "org.apache.pekko"             %% "pekko-connectors-mongodb"    % pekkoVersion,
    "org.apache.pekko"             %% "pekko-stream"                % pekkoVersion,
    "org.apache.pekko"             %% "pekko-actor-typed"           % pekkoVersion,
    "org.apache.pekko"             %% "pekko-serialization-jackson" % pekkoVersion,
    "org.typelevel"                %% "cats-core"                   % "2.12.0",
    "com.github.pathikrit"         %% "better-files"                % "3.9.2",
    "org.quartz-scheduler"          % "quartz"                      % "2.3.2",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"        % "2.17.2"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % mongoHmrcVersion,
    "org.apache.pekko"  %% "pekko-http-testkit"      % "1.0.1"
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test
}
