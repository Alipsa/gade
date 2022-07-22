#!/usr/bin/env bash

gradle clean build runtimeZip

echo "Release files are:"
echo "******************"
ls -1 -r build/libs/*.jar
ls -1 -r build/*.zip
