import sbt.*

object AppDependencies {
  private lazy val mongoHmrcVersion = "2.5.0"
  private lazy val bootstrapVersion = "9.8.0"
  private lazy val pekkoVersion     = "1.1.3"

  lazy val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30"   % bootstrapVersion,
    "uk.gov.hmrc"                  %% "play-json-union-formatter"   % "1.22.0",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"          % mongoHmrcVersion,
    "org.apache.pekko"             %% "pekko-connectors-mongodb"    % "1.1.0",
    "org.apache.pekko"             %% "pekko-stream"                % pekkoVersion,
    "org.apache.pekko"             %% "pekko-actor-typed"           % pekkoVersion,
    "org.apache.pekko"             %% "pekko-serialization-jackson" % pekkoVersion,
    "org.typelevel"                %% "cats-core"                   % "2.13.0",
    "com.github.pathikrit"         %% "better-files"                % "3.9.2",
    "org.quartz-scheduler"          % "quartz"                      % "2.5.0",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"        % "2.18.2"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % mongoHmrcVersion,
    "org.apache.pekko"  %% "pekko-http-testkit"      % "1.1.0"
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test
}
