#!/usr/bin/env bash

SCRIPT_DIR="$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

javacmd=$(command -v java)

function switchJava() {
  if [[ -f ~/.sdkman/bin/sdkman-init.sh ]]; then
    source ~/.sdkman/bin/sdkman-init.sh
  fi
  if [[ $(command -v jdk17) ]]; then
    source jdk17
  fi
}

if [[ -f "$javacmd" ]]; then
        javaVersion=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
        if [[ (($javaVersion -eq 17)) ]]; then
                echo "java 17 is already the active version"
        else
                switchJava
        fi
else
        switchJava
fi

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

function toMsysPath {
  echo "/$1" | sed -e 's/\\/\//g' -e 's/://'
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

echo "- Copy ${PLATFORM} dist to $TARGET_DIR"
cp -r "build/image/gade-${PLATFORM}/." "$TARGET_DIR" || exit

if [[ -d "${LINK_DIR}" || -L "${LINK_DIR}" ]]; then
  echo "- Remove existing dir link (or dir)"
  rm -rf "${LINK_DIR}" || exit
fi
echo "- Create dir link"
if [[ "$PLATFORM" == "win" ]]; then
  if [[ "$OSTYPE" == "msys" ]]; then
    srcDir="$(toMsysPath "${TARGET_DIR}")"
    lnkBase="$(dirname "${LINK_DIR}")"
    lnkDir="$(toMsysPath "$lnkBase")\\gade"
  else
    srcDir="$(wslpath -w "${TARGET_DIR}")"
    lnkBase="$(dirname "${LINK_DIR}")"
    lnkDir="$(wslpath -w "$lnkBase")\\gade"
  fi
  echo "- creating junction to $lnkDir from $srcDir"
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

