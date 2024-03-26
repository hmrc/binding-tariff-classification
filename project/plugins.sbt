resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"
resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(
  Resolver.ivyStylePatterns
)

// To resolve a bug with version 2.x.x of the scoverage plugin - https://github.com/sbt/sbt/issues/6997
// Try to remove when sbt 1.8.0+ and scoverage is 2.0.7+
ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

addSbtPlugin("com.typesafe.play"         % "sbt-plugin"            % "2.8.21")
addSbtPlugin("net.virtual-void"          % "sbt-dependency-graph"  % "0.10.0-RC1")
addSbtPlugin("org.irundaia.sbt"          % "sbt-sassify"           % "1.4.12")
addSbtPlugin("org.scalastyle"            % "scalastyle-sbt-plugin" % "1.0.0" exclude ("org.scala-lang.modules", "scala-xml_2.12"))
addSbtPlugin("org.scoverage"             % "sbt-scoverage"         % "2.0.11")
addSbtPlugin("uk.gov.hmrc"               % "sbt-auto-build"        % "3.20.0")
addSbtPlugin("uk.gov.hmrc"               % "sbt-distributables"    % "2.5.0")
addSbtPlugin("org.scalameta"             % "sbt-scalafmt"          % "2.5.2")
addSbtPlugin("com.timushev.sbt"          % "sbt-updates"           % "0.6.4")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"          % "0.3.3")
