#!/usr/bin/env bash
#######################################
# maven based launcher
# requires maven 3.6.3 or later
# and java 21 or later installed
#######################################
set -e
version='1.0.0-SNAPSHOT'
echo "Locating local repository..."
localRepo=$(mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout)

pomfile="gade-runner-$version.pom"
pomfilePath="$localRepo/se/alipsa/gade-runner/$version/$pomfile"

if [[ "$version" == *SNAPSHOT ]]; then
  repo='-DremoteRepositories=snapshots::default::https://oss.sonatype.org/content/repositories/snapshots'
else
  repo='-DremoteRepositories=central::default::http://repo1.maven.apache.org/maven2'
fi
echo "Updating gade dependencies"
if [[ ! -f ./$pomfile ]]; then
  if [[ ! -f "$pomfilePath" ]]; then
    mvn -U dependency:get "$repo" -Dartifact="se.alipsa:gade-runner:$version"
  fi
  cp -f "$pomfilePath" .
fi
echo "Starting Gade..."
mvn -q -f "$pomfile" javafx:run
rm "$pomfile"

