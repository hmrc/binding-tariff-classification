#!/usr/bin/env bash
sbt scalafmtAll scalastyleAll compile coverage test IntegrationTest/test coverageOff coverageReport dependencyUpdates
