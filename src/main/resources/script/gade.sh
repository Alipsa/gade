#!/usr/bin/env bash

# Resolve symlinks so the launcher can be linked into a user's PATH.
SOURCE="${BASH_SOURCE[0]}"
while [[ -h "${SOURCE}" ]]; do
  DIR="$( cd -P "$( dirname "${SOURCE}" )" >/dev/null 2>&1 && pwd )"
  SOURCE="$(readlink "${SOURCE}")"
  [[ "${SOURCE}" != /* ]] && SOURCE="${DIR}/${SOURCE}"
done
DIR="$( cd -P "$( dirname "${SOURCE}" )" >/dev/null 2>&1 && pwd )"
cd -- "${DIR}" || { echo "Failed to cd to ${DIR}" >&2; exit 1; }

function notify() {
  echo "$1"
  if command -v zenity > /dev/null 2>&1; then
    zenity --info --text="$1"
  elif command -v notify-send > /dev/null 2>&1; then
    notify-send "$1"
  elif [[ -f /usr/bin/osascript ]]; then
    # must define full path to osascript for this to work as a mac app.
    /usr/bin/osascript -e "display notification \"$1\" with title \"Gade\""
  elif [[ "${OSTYPE}" == "msys" ]]; then
    msg "${USERNAME}" "$1" /time:30
  fi
}

# On gitbash (msys) we need to convert to Windows-style paths for Java.
function winpath {
  echo "${1}" | sed -e 's/^\///' -e 's/\//\\/g' -e 's/^./\0:/'
}

LIB_DIR="${DIR}/lib"

# The runtime distribution has a bundled JDK and reorganized lib directory.
# Maven-installed distributions use the older layout and the system JDK.
if [[ -d "${LIB_DIR}/app" ]]; then
  PACKAGED_RUNTIME=true
else
  PACKAGED_RUNTIME=false
fi

# Allow for any kind of customization of variables or paths etc. without having
# to change this script, which would otherwise be overwritten on installation.
if [[ -f "${DIR}/env.sh" ]]; then
  source "${DIR}/env.sh"
fi

# FreeBSD and similar systems are not supported because there are no JavaFX
# platform jars for them.
if [[ "${OSTYPE}" == "linux-gnu"* ]]; then
  OS=linux
elif [[ "${OSTYPE}" == "darwin"* ]]; then
  OS=mac
else
  # msys, cygwin, win32
  OS=win
fi

MODULES=javafx.controls,javafx.media,javafx.web,javafx.swing
SPLASHTIME="${SPLASH_TIME:-2}"

if [[ "${PACKAGED_RUNTIME}" == true ]]; then
  export JAVA_HOME="${DIR}"
  BIN_DIR="${DIR}/bin"
  export PATH="${JAVA_HOME}/bin:${PATH}:${LIB_DIR}"

  if [[ -f "${DIR}/conf/jaas.conf" ]]; then
    JAVA_OPTS="${JAVA_OPTS} -Djava.security.auth.login.config=${DIR}/conf/jaas.conf"
  fi

  if [[ "${OS}" == "mac" ]]; then
    JAVA_OPTS="${JAVA_OPTS} -Xdock:name=gade -Xdock:icon=${DIR}/Contents/Resources/gade.icns"
  fi

  if [[ "${OS}" == "win" ]]; then
    if [[ -z "${JAVA_CMD}" ]]; then
      JAVA_CMD="javaw"
    fi
    CLASSPATH="$(winpath "${LIB_DIR}")\\app\\*;$(winpath "${LIB_DIR}")\\groovy\\*"
    LD_PATH="$(winpath "${LIB_DIR}")"

    # Fixes bug: Unable to get Charset 'cp65001' for property
    # 'sun.stdout.encoding'.
    JAVA_OPTS="${JAVA_OPTS} -Dsun.stdout.encoding=UTF-8 -Dsun.err.encoding=UTF-8"
    # shellcheck disable=SC2068
    start "${BIN_DIR}\\${JAVA_CMD}" \
      $JAVA_OPTS \
      -Dsplash.minSeconds="${SPLASHTIME}" \
      --enable-native-access=javafx.graphics,javafx.media,javafx.web,ALL-UNNAMED \
      --add-opens=java.base/java.lang=ALL-UNNAMED \
      --add-opens=java.base/java.util=ALL-UNNAMED \
      --add-opens=java.base/java.io=ALL-UNNAMED \
      --add-opens=java.base/java.net=ALL-UNNAMED \
      -Djava.library.path="${LD_PATH}" \
      --module-path "${LIB_DIR}/${OS}" --add-modules "${MODULES}" \
      -cp "${CLASSPATH}" se.alipsa.gade.Gade
  else
    JAVA_CMD="java"
    LD_PATH="${LIB_DIR}"
    # shellcheck disable=SC2068
    "${BIN_DIR}/${JAVA_CMD}" \
      $JAVA_OPTS \
      -Dsplash.minSeconds="${SPLASHTIME}" \
      --enable-native-access=javafx.graphics,javafx.media,javafx.web,ALL-UNNAMED \
      --add-opens=java.base/java.lang=ALL-UNNAMED \
      --add-opens=java.base/java.util=ALL-UNNAMED \
      --add-opens=java.base/java.io=ALL-UNNAMED \
      --add-opens=java.base/java.net=ALL-UNNAMED \
      -Djava.library.path="${LD_PATH}" \
      --module-path "${LIB_DIR}/${OS}" --add-modules "${MODULES}" \
      -cp "${LIB_DIR}/app/*:${LIB_DIR}/groovy/*" \
      se.alipsa.gade.Gade &
  fi
else
  # Legacy Maven-installed distribution.
  if [[ -z "${JAVA_CMD}" ]]; then
    if command -v java > /dev/null 2>&1; then
      JAVA_CMD="$(command -v java)"
    else
      MSG="Failed to find JAVA_CMD in env.sh or java on PATH, cannot continue"
      notify "${MSG}"
      exit 1
    fi
  fi

  LD_PATH="${LIB_DIR}"
  if [[ "${OS}" == "mac" ]]; then
    JAVA_OPTS="${JAVA_OPTS} -Xdock:name=gade -Xdock:icon=${DIR}/Contents/Resources/gade.icns"
  fi

  if [[ "${OS}" == "win" ]]; then
    CLASSPATH="$(winpath "${LIB_DIR}")\\*"
    LD_PATH="$(winpath "${LIB_DIR}")"
    # shellcheck disable=SC2068
    start "${JAVA_CMD}" \
      $JAVA_OPTS \
      -Dsplash.minSeconds="${SPLASHTIME}" \
      --enable-native-access=javafx.graphics,javafx.media,javafx.web \
      -Djava.library.path="${LD_PATH}" \
      --module-path "$(winpath "${LIB_DIR}")\\jfx" --add-modules "${MODULES}" \
      -cp "${CLASSPATH}" se.alipsa.gade.Gade
  else
    # shellcheck disable=SC2068
    "${JAVA_CMD}" \
      $JAVA_OPTS \
      -Dsplash.minSeconds="${SPLASHTIME}" \
      --enable-native-access=javafx.graphics,javafx.media,javafx.web \
      --module-path "${LIB_DIR}/jfx" --add-modules "${MODULES}" \
      -Djava.library.path="${LD_PATH}" \
      -cp "${LIB_DIR}/*" se.alipsa.gade.Gade &
  fi
fi
