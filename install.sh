#!/usr/bin/env bash

SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

source ~/.sdkman/bin/sdkman-init.sh
source jdk17

function platform {
  if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    echo "linux"
  elif [[ "$OSTYPE" == "darwin"* ]]; then
    echo "mac"
  else
    echo "win"
  fi
}
PREF_TARGET="${1:-$HOME/programs/}"
PLATFORM="${2:-$(platform)}"

cd "${SCRIPT_DIR}" || exit
echo "- Building Gade"
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


TARGET_DIR="${PREF_TARGET}/gade-${RELEASE_TAG}"

LINK_DIR=$(dirname "${TARGET_DIR}")/gade

if ls "$LINK_DIR"/env.* 1> /dev/null 2>&1; then
  echo "- Saving env files"
  tmp_dir=$(mktemp -d -t ci-XXXXXXXXXX) || exit
  cp "$LINK_DIR"/env.* "${tmp_dir}/" || exit
fi

if [[ -d "$TARGET_DIR" ]]; then
  echo "- Remove existing Gade installation"
  rm -rf "$TARGET_DIR" || exit
fi
mkdir -p "$TARGET_DIR" || exit

echo "- Copy ${PLATFORM} dist"
cp -r "build/image/gade-${PLATFORM}/." "$TARGET_DIR" || exit

if [[ -d "${LINK_DIR}" || -L "${LINK_DIR}" ]]; then
  echo "- Remove existing dir link (or dir)"
  rm -rf "${LINK_DIR}" || exit
fi
echo "- Create dir link"
if [[ "$PLATFORM" == "win" ]]; then
  srcDir="$(wslpath -w "${TARGET_DIR}")"
  lnkBase="$(dirname "${LINK_DIR}")"
  lnkDir="$(wslpath -w "$lnkBase")\\gade"
  # echo "creating junction to $lnkDir from $srcDir"
  cmd.exe /c "mklink /J $lnkDir $srcDir"
else
  chmod +x "${TARGET_DIR}"/*.sh  || exit
  ln -sf "${TARGET_DIR}" "${LINK_DIR}" || exit
fi

if [[ -d "${tmp_dir}" ]]; then
  echo "- Restore env files"
  cp "${tmp_dir}"/env.* "$TARGET_DIR"/ || exit
  rm -rf "${tmp_dir}" || exit
fi
echo "Finished at $(date)"

