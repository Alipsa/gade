#!/usr/bin/env bash
set -e
echo "Publish to local repo"
./gradlew clean publishToMavenLocal
mvn -f gade-runner.xml install
echo "TODO: Publish to maven central (ossrh)"
#./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
#mvn -Prelease -f gade-runner.xml deploy
echo "Done!"