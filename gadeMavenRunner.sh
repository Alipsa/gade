#!/usr/bin/env bash
#######################################
# maven based launcher
# requires maven and java 21 installed
#######################################
set -e
version='1.0.0-SNAPSHOT'
localRepo=$(mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout)

pomfile="gade-runner-$version.pom"
pomfilePath="$localRepo/se/alipsa/gade-runner/$version/$pomfile"

if [[ ! -f ./$pomfile ]]; then
  if [[ ! -f "$pomfilePath" ]]; then
    mvn dependency:get -Dartifact="se.alipsa:gade-runner:$version"
  fi
  cp "$pomfilePath" .
fi

mvn -q -f "$pomfile" javafx:run

