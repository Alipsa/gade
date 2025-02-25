#!/usr/bin/env bash
set -e
echo "Publish to local repo"
./gradlew clean publishToMavenLocal
mvn -f gade-runner.pom install
if [[ -f ~/.gradle/gradle.properties ]]; then
  source ~/.gradle/gradle.properties
  if [[ -n "$sonatypeUsername" ]]; then
    echo "Publishing Gade to Sonatype Maven Central"
    ./gradlew release
    echo "Publishing Gade Runner to Sonatype Maven Central"
    mvn -Prelease -f gade-runner.pom deploy
  fi
else
  echo "Skipped publish to sonatype"
fi
echo "Done!"