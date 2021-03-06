resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"
resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
resolvers += Resolver.typesafeRepo("releases")

addSbtPlugin("com.typesafe.play"         % "sbt-plugin"             % "2.7.9")
addSbtPlugin("net.virtual-void"          % "sbt-dependency-graph"   % "0.10.0-RC1")
addSbtPlugin("org.irundaia.sbt"          % "sbt-sassify"            % "1.4.11")
addSbtPlugin("org.scalastyle"            %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("org.scoverage"             % "sbt-scoverage"          % "1.5.1")
addSbtPlugin("uk.gov.hmrc"               % "sbt-auto-build"         % "3.0.0")
addSbtPlugin("uk.gov.hmrc"               % "sbt-distributables"     % "2.1.0")
addSbtPlugin("org.scalameta"             % "sbt-scalafmt"           % "2.4.2")
addSbtPlugin("com.timushev.sbt"          % "sbt-updates"            % "0.5.1")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"           % "0.1.14")

