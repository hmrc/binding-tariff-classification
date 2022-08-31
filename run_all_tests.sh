#!/usr/bin/env bash
sbt scalafmtAll scalastyle compile coverage test it:test coverageOff coverageReport dependencyUpdates