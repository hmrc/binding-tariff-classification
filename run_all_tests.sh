#!/usr/bin/env bash
sbt scalafmtAll scalastyleAll compile coverage test it:test coverageOff coverageReport dependencyUpdates