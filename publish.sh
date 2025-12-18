#!/usr/bin/env bash
set -e
echo "Publish to local repo"
./gradlew clean publishToMavenLocal -g ./.gradle-user
mvn -f gade-runner.pom install
if [[ -f ~/.gradle/gradle.properties ]]; then
  awk -f readproperties.awk < ~/.gradle/gradle.properties > gradleprops.sh
  source gradleprops.sh
  if [[ -n "$sonatypeUsername" ]]; then
    echo "Publishing Gade to Sonatype Maven Central"
    ./gradlew release -g ./.gradle-user
    echo "Publishing Gade Runner to Sonatype Maven Central"
    mvn -Prelease -f gade-runner.pom deploy
  fi
  rm gradleprops.sh
else
  echo "Skipped publish to sonatype"
fi
echo "Done!"