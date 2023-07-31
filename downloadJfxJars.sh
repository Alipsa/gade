#!/usr/bin/env bash

PLATFORM=$1
# Workaround if building for windows from linux and local repo does not have windows javafx jars:
jfxVersion=20.0.1

function fetchJfxArtifacts {
  qualifier=$1
  pluginVer=3.5.0
  mvn org.apache.maven.plugins:maven-dependency-plugin:${pluginVer}:get -Dartifact=org.openjfx:javafx-base:${jfxVersion}:jar:"$qualifier"
  mvn org.apache.maven.plugins:maven-dependency-plugin:${pluginVer}:get -Dartifact=org.openjfx:javafx-controls:${jfxVersion}:jar:"$qualifier"
  mvn org.apache.maven.plugins:maven-dependency-plugin:${pluginVer}:get -Dartifact=org.openjfx:javafx-graphics:${jfxVersion}:jar:"$qualifier"
}

# see https://repo1.maven.org/maven2/org/openjfx/javafx/20/javafx-20.pom for info on qualifier names
# hint: its the javafx.platform property, not the id
if [[ ! "${PLATFORM}" == "win" ]]; then
  echo "- Downloading windows javafx jars"
  fetchJfxArtifacts win
fi
if [[ ! "${PLATFORM}" == "linux" ]]; then
  echo "- Downloading linux javafx jars"
  fetchJfxArtifacts linux
fi
if [[ ! "${PLATFORM}" == "mac" ]]; then
  echo "- Downloading mac javafx jars"
  fetchJfxArtifacts mac-aarch64
fi