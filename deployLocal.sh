#!/usr/bin/env bash

SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

source ~/.sdkman/bin/sdkman-init.sh
source jdk17

cd "${SCRIPT_DIR}" || exit
echo "building Gride"
mvn clean package || exit 1

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
if [[ -z "${1}" ]]; then
  TARGET_DIR="$HOME/programs/gride"
else
  TARGET_DIR="${1}"
fi
cd "${DIR}" || exit 1

PROPERTY_FILE=version.properties

function getProperty {
   PROP_KEY=$1
   PROP_VALUE=$(cat $PROPERTY_FILE | grep "$PROP_KEY" | cut -d'=' -f2)
   echo "$PROP_VALUE" | xargs
}

VERSION=$(getProperty "version")
JAR_NAME=$(getProperty "jar.name")
RELEASE_TAG=$(getProperty "release.tag")

mkdir -p "$TARGET_DIR"
mkdir -p "$TARGET_DIR/lib"
find "$TARGET_DIR" -name \*.jar -type f -delete
find "$TARGET_DIR" -name \*.pom -type f -delete
unzip -o "$DIR/target/gride-$RELEASE_TAG-dist.zip" -d "$TARGET_DIR"

echo "Finished at $(date)"

