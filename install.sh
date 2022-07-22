#!/usr/bin/env bash

SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

source ~/.sdkman/bin/sdkman-init.sh
source jdk17

cd "${SCRIPT_DIR}" || exit
echo "- Building Grade"
./gradlew clean runtime || exit 1

PROPERTY_FILE=version.properties

function getProperty {
   PROP_KEY=$1
   PROP_VALUE=$(cat $PROPERTY_FILE | grep "$PROP_KEY" | cut -d'=' -f2)
   echo "$PROP_VALUE" | xargs
}

#VERSION=$(getProperty "version")
#JAR_NAME=$(getProperty "jar.name")
RELEASE_TAG=$(getProperty "release.tag")

if [[ -z "${1}" ]]; then
  TARGET_DIR="$HOME/programs/grade-${RELEASE_TAG}"
else
  TARGET_DIR="${1}"
fi


if ls "$TARGET_DIR"/env.* 1> /dev/null 2>&1; then
  echo "- Saving env files"
  tmp_dir=$(mktemp -d -t ci-XXXXXXXXXX) || exit
  cp "$TARGET_DIR"/env.* "${tmp_dir}/" || exit
fi
if [[ -d "$TARGET_DIR" ]]; then
  echo "- Remove existing grade installation"
  rm -rf "$TARGET_DIR" || exit
fi

mkdir -p "$TARGET_DIR" || exit

if [[ "$OSTYPE" == "linux-gnu"* ]]; then
  echo "- Copy linux dist"
  cp -r build/image/grade-linux/. "$TARGET_DIR" || exit
elif [[ "$OSTYPE" == "darwin"* ]]; then
  echo "- Copy mac dist"
  cp -r build/image/grade-mac/. "$TARGET_DIR" || exit
else
  echo "- Copy windows dist"
  cp -r build/image/grade-win/. "$TARGET_DIR" || exit
fi

LINK_DIR=$(dirname "${TARGET_DIR}")/grade
if [[ -d "${LINK_DIR}" ]]; then
  echo "- Remove existing dir link (or dir)"
  rm -rf "${LINK_DIR}" || exit
fi
echo "- Create dir link"
ln -s "${TARGET_DIR}" "${LINK_DIR}" || exit

if [[ -d "${tmp_dir}" ]]; then
  echo "- Restore env files"
  cp "${tmp_dir}"/env.* "$TARGET_DIR"/ || exit
  rm -rf "${tmp_dir}" || exit
fi
echo "Finished at $(date)"

