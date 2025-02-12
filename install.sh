#!/usr/bin/env bash

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"

javacmd=$(command -v java)

function switchJava() {
  if [[ -f ~/.sdkman/bin/sdkman-init.sh ]]; then
    source ~/.sdkman/bin/sdkman-init.sh
  fi
  if [[ $(command -v jdk21) ]]; then
    source jdk21
  fi
}

if [[ -f "$javacmd" ]]; then
  javaVersion=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
  if [[ (($javaVersion -eq 21)) ]]; then
    echo "java 21 is already the active version"
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

function getProperty {
  PROP_KEY=$1
  PROP_VALUE=$(cat $PROPERTY_FILE | grep "$PROP_KEY" | cut -d'=' -f2)
  echo "$PROP_VALUE" | xargs
}

function toMsysPath {
  echo "/$1" | sed -e 's/\\/\//g' -e 's/://'
}

function toWinPath {
  echo "$1" | sed -e 's/^\///' -e 's/\//\\/g' -e 's/^./\0:/'
}

PREF_TARGET="${1:-$HOME/programs}"
if [[ "msys" == "${OSTYPE}" ]]; then
  default_target=$(toMsysPath "$USERPROFILE/programs")
  PREF_TARGET="${1:-$default_target}"
fi
PLATFORM="${2:-$(platform)}"

echo "PREF_TARGET=${PREF_TARGET}, PLATFORM=${PLATFORM} "

cd "${SCRIPT_DIR}" || exit

# dependencies are declared in the gradle build script, no need to download outside of that
#./downloadJfxJars.sh "$PLATFORM"

echo "- Building Gade"
./gradlew clean build runtime || exit 1

PROPERTY_FILE=version.properties

#VERSION=$(getProperty "version")
#JAR_NAME=$(getProperty "jarName")
RELEASE_TAG=$(getProperty "releaseTag")

TARGET_DIR="${PREF_TARGET}/gade-${RELEASE_TAG}"

LINK_DIR=$(dirname "${TARGET_DIR}")/gade

if ls "$LINK_DIR"/env.* 1>/dev/null 2>&1; then
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
    srcDir="$(toWinPath "${TARGET_DIR}")"
    lnkBase="$(dirname "${LINK_DIR}")"
    lnkDir="$(toWinPath "${lnkBase}/gade")"
    if [[ ! -d ${srcDir} ]]; then
      echo "source dir for installation: ${srcDir} does not exist!"
      exit 1
    fi
    if [[ ! -d ${lnkBase} ]]; then
      echo "base dir for installation: ${lnkBase} does not exist!"
      exit 1
    fi
    echo "- creating junction to $lnkDir from $srcDir"
    cmd.exe "/c mklink /J $lnkDir $srcDir"
  else
    srcDir="$(wslpath -w "${TARGET_DIR}")"
    lnkBase="$(dirname "${LINK_DIR}")"
    lnkDir="$(wslpath -w "$lnkBase")\\gade"
    echo "- creating junction to $lnkDir from $srcDir"
    /mnt/c/windows/system32/cmd.exe /c "mklink /J $lnkDir $srcDir"
  fi
else
  chmod +x "${TARGET_DIR}"/*.sh || exit
  echo "- creating symlink to ${TARGET_DIR} from ${LINK_DIR}"
  ln -sf "${TARGET_DIR}" "${LINK_DIR}" || exit
fi

if [[ -d "${tmp_dir}" ]]; then
  echo "- Restore env files"
  cp "${tmp_dir}"/env.* "$TARGET_DIR"/ || exit
  rm -rf "${tmp_dir}" || exit
fi

if [[ "${PLATFORM}" == "mac" ]]; then
  echo "- creating mac application"
  APP_DIR=$(dirname "${TARGET_DIR}")/gade.app
  if [[ -d "${APP_DIR}" ]]; then
    echo "- backing up previous installation"
    if [[ -d "${APP_DIR}.old" ]]; then
      rm -rf "${APP_DIR}.old"
    fi
    mv "$APP_DIR" "${APP_DIR}.old"
  else
    mkdir "$APP_DIR"
  fi

  cp -r "$TARGET_DIR" "$APP_DIR"
  CONTENT_DIR="${APP_DIR}/Contents"
  MACOS_DIR="${CONTENT_DIR}/MacOS"
  RESOURCE_DIR="${CONTENT_DIR}/Resources"
  mkdir -p "$MACOS_DIR"
  mkdir -p "$RESOURCE_DIR"
  cp "$SCRIPT_DIR/src/main/mac/Info.plist" "$CONTENT_DIR/"
  cp "$SCRIPT_DIR/src/main/mac/gade.icns" "${RESOURCE_DIR}/"
  cp "$SCRIPT_DIR/src/main/mac/gade" "${MACOS_DIR}/"
  chmod +x "${MACOS_DIR}/gade"
  SetFile -a B "${APP_DIR}"
fi
echo "Finished at $(date)"
