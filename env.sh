#!/usr/bin/env bash

source ~/.sdkman/bin/sdkman-init.sh
. jdk11
# In case ride ever has any swing stuff, then the swing setting is the one that takes precedence
export GDK_SCALE=2
# usScale is the scaling option for pure javafx"
export JAVA_OPTS="-Dglass.gtk.uiScale=200% -Djdk.gtk.version=2 -Dgroovy.grape.report.downloads=true"
