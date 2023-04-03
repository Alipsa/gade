#!/usr/bin/env bash
# dependencyUpdates only checks maven central
url=https://repo.gradle.org/gradle/libs-releases/org/gradle/gradle-tooling-api/maven-metadata.xml
echo "Checking latest version of the gradle tooling api"

function checkVersion {
    curl -s $1 | grep "artifactId\|latest\|release\|lastUpdated"
}
latestVersion=$(checkVersion "https://repo.gradle.org/gradle/libs-releases/org/gradle/gradle-tooling-api/maven-metadata.xml")

if echo "${latestVersion}" | grep -q "milestone\|rc"; then
  curl -s ${url}
  echo "failed to find latest release version as latest is either rc or milestone"
else
  echo "${latestVersion}"
fi

./gradlew dependencyUpdates -Drevision=release

