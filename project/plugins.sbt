resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"
resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(
  Resolver.ivyStylePatterns
)

addSbtPlugin("org.playframework"         % "sbt-plugin"         % "3.0.3")
addSbtPlugin("io.github.irundaia"        % "sbt-sassify"        % "1.5.2")
addSbtPlugin("org.scoverage"             % "sbt-scoverage"      % "2.0.12")
addSbtPlugin("uk.gov.hmrc"               % "sbt-auto-build"     % "3.22.0")
addSbtPlugin("uk.gov.hmrc"               % "sbt-distributables" % "2.5.0")
addSbtPlugin("org.scalameta"             % "sbt-scalafmt"       % "2.5.2")
addSbtPlugin("com.timushev.sbt"          % "sbt-updates"        % "0.6.4")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"       % "0.3.3")
