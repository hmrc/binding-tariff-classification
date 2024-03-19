#!/usr/bin/env bash
sbt clean compile scalafmtAll scalastyleAll coverage test it/test coverageReport dependencyUpdates
