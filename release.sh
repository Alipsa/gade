#!/usr/bin/env bash
source ~/.sdkman/bin/sdkman-init.sh
source jdk21

gradle clean build runtimeZip

echo "Release files are:"
echo "******************"
ls -1 -r build/libs/*.jar
ls -1 -r build/*.zip
