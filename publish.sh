#!/usr/bin/env bash
set -e
echo "Publish to local repo"
./gradlew clean publishToMavenLocal
mvn -f gade-runner.xml install
echo "Publishing Gade to Sonatype Maven Central"
./gradlew release
echo "Publishing Gade Runner to Sonatype Maven Central"
mvn -Prelease -f gade-runner.xml deploy
echo "Done!"