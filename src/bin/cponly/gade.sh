#!/usr/bin/env bash

# This start script is meant to be used in the binary distribiution of Gade where the jdk is bundled

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

function notify() {
  echo "$1"
  if command -v zenity > /dev/null 2>&1; then
    zenity --info --text="$1"
  elif command -v notify-send > /dev/null 2>&1; then
    notify-send "$1"
  elif [[ -f /usr/bin/osascript ]]; then
      # must define full path to osascript for his to work as an mac app.
      /usr/bin/osascript -e "display notification \"$1\" with title \"Gade\""
  elif [[ "${OSTYPE}" == "msys" ]]; then
    msg ${USERNAME} "$1" /time:30
  fi
}

# on gitbash (msys) we need to convert to windows style path for java
function winpath {
  echo "${1}" | sed -e 's/^\///' -e 's/\//\\/g' -e 's/^./\0:/'
}

function posixpath {
  if [[ "${OSTYPE}" == "msys" ]]; then
    echo "${1}" | sed -e 's/\\/\//g' -e 's/://' -e '/\/$/! s|$|/|'
  else
    echo "/${1}" | sed -e 's/\\/\//g' -e 's/://' -e '/\/$/! s|$|/|'
  fi
}

cd "${DIR}" || { notify "Failed to cd to $DIR"; exit 1; }

PROPERTY_FILE=version.properties

function getProperty {
   PROP_KEY=$1
   PROP_VALUE=$(cat $PROPERTY_FILE | grep "$PROP_KEY" | cut -d'=' -f2)
   echo "$PROP_VALUE" | xargs
}

VERSION=$(getProperty "version")
JAR_NAME=$(getProperty "jarName")
RELEASE_TAG=$(getProperty "releaseTag")

export JAVA_HOME="${DIR}"
BIN_DIR="${DIR}/bin"
LIB_DIR="${DIR}/lib"
export PATH=$JAVA_HOME/bin:$PATH:${LIB_DIR}

# Allow for any kind of customization of variables or paths etc. without having to change this script
# which would otherwise be overwritten on a subsequent install.
if [[ -f $DIR/env.sh ]]; then
  source "$DIR/env.sh"
fi

# freebsd and similar not supported with this construct, there are no javafx platform jars for those anyway
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
  OS=linux
elif [[ "$OSTYPE" == "darwin"* ]]; then
  OS=mac
else
  # msys, cygwin, win32
  OS=win
fi

JAVA_OPTS="$JAVA_OPTS -Djava.security.auth.login.config=$DIR/conf/jaas.conf"

# Note: RootLoader system classloader is not used in packaged distributions
# All dependencies are already bundled in the lib directory

if [[ "${OS}" == "mac" ]]; then
  JAVA_OPTS="$JAVA_OPTS -Xdock:name=gade -Xdock:icon=$DIR/Contents/Resources/gade.icns"
fi

MODULES=javafx.controls,javafx.media,javafx.web,javafx.swing

if [[ "${OS}" == "win" ]]; then
  if [[ -z "$JAVA_CMD" ]]; then
	  JAVA_CMD="javaw"
	fi
	CLASSPATH="$(winpath "${LIB_DIR}")/*"
	LD_PATH=$(winpath "${LIB_DIR}")

	# Fixes bug  Unable to get Charset 'cp65001' for property 'sun.stdout.encoding'
	JAVA_OPTS="${JAVA_OPTS} -Dsun.stdout.encoding=UTF-8 -Dsun.err.encoding=UTF-8"
	start "${BIN_DIR}\${JAVA_CMD}" \
		$JAVA_OPTS \
		--enable-native-access=javafx.graphics,javafx.media,javafx.web,ALL-UNNAMED \
		-Djava.library.path="${LD_PATH}" \
		--module-path ${LIB_DIR}/$OS --add-modules ${MODULES} \
		-cp "${LIB_DIR}/*" se.alipsa.gade.splash.SplashScreen
	# shellcheck disable=SC2068
	start "${BIN_DIR}\${JAVA_CMD}" \
		$JAVA_OPTS \
		--enable-native-access=javafx.graphics,javafx.media,javafx.web,ALL-UNNAMED \
		--add-opens=java.base/java.lang=ALL-UNNAMED \
		--add-opens=java.base/java.util=ALL-UNNAMED \
		--add-opens=java.base/java.io=ALL-UNNAMED \
		--add-opens=java.base/java.net=ALL-UNNAMED \
		-Djava.library.path="${LD_PATH}" \
		--module-path ${LIB_DIR}/$OS --add-modules ${MODULES} \
		-cp "${CLASSPATH}" se.alipsa.gade.Gade

else
	JAVA_CMD="java"
	LD_PATH="${LIB_DIR}"
	"${BIN_DIR}/${JAVA_CMD}" \
	$JAVA_OPTS \
	--enable-native-access=javafx.graphics,javafx.media,javafx.web,ALL-UNNAMED \
	-Djava.library.path="${LD_PATH}" \
	--module-path ${LIB_DIR}/$OS --add-modules ${MODULES} \
	-cp "${LIB_DIR}/*" \
	se.alipsa.gade.splash.SplashScreen &
	# shellcheck disable=SC2068
	"${BIN_DIR}/${JAVA_CMD}" \
		$JAVA_OPTS \
		--enable-native-access=javafx.graphics,javafx.media,javafx.web,ALL-UNNAMED \
		--add-opens=java.base/java.lang=ALL-UNNAMED \
		--add-opens=java.base/java.util=ALL-UNNAMED \
		--add-opens=java.base/java.io=ALL-UNNAMED \
		--add-opens=java.base/java.net=ALL-UNNAMED \
		-Djava.library.path="${LD_PATH}" \
		--module-path ${LIB_DIR}/$OS --add-modules ${MODULES} \
		-cp "${LIB_DIR}/*" \
	se.alipsa.gade.Gade &
fi