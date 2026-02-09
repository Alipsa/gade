#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
LOG="$DIR/startup.log"
echo "Starting gade.sh" > "$LOG"

echo "Define notify function" >> "$LOG"
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
    msg "${USERNAME}" "$1" /time:30
  fi
}

# on gitbash (msys) we need to convert to windows style path for java
echo "Define winpath function" >> "$LOG"
function winpath {
  echo "${1}" | sed -e 's/^\///' -e 's/\//\\/g' -e 's/^./\0:/'
}

echo "cd to script dir" >> "$LOG"
cd "${DIR}" || { notify "Failed to cd to $DIR"; exit 1; }

JAR_NAME=$(ls lib/gade-*.jar)
JAR_NAME=$(basename "$JAR_NAME")

LIB_DIR=${DIR}/lib
export PATH=$PATH:${LIB_DIR}

# Allow for any kind of customization of variables or paths etc. without having to change this script
# which would otherwise be overwritten on a subsequent install.
echo "Call env.sh" >> "$LOG"
if [[ -f $DIR/env.sh ]]; then
  source "$DIR/env.sh"
fi

if [[ -z ${JAVA_CMD+x} ]]; then
  if command -v java > /dev/null 2>&1; then
    JAVA_CMD=$(command -v java)
  else
    msg="Failed to find JAVA_CMD variable in env.sh or java on the path, cannot continue"
    notify "$msg"
    echo "$msg" >> "$LOG"
    exit 1
  fi
fi
echo "JAVA_CMD=$JAVA_CMD" >> "$LOG"

# freebsd and similar not supported with this construct, there are no javafx platform jars for those anyway
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
  OS=linux
elif [[ "$OSTYPE" == "darwin"* ]]; then
  OS=mac
else
  # msys, cygwin, win32
  OS=win
fi
echo "OS is $OS" >> "$LOG"
# Note: It is possible to force the initial package loader by adding:
# -DConsoleComponent.PackageLoader=ClasspathPackageLoader
# to the command below, but even better to add it to JAVA_OPTS variable in env.sh
MODULES="javafx.controls,javafx.media,javafx.web,javafx.swing"

if [[ "${OS}" == "mac" ]]; then
  JAVA_OPTS="$JAVA_OPTS -Xdock:name=gade -Xdock:icon=\"$DIR/Contents/Resources/gade.icns\""
fi
echo "JAVA_OPTS=$JAVA_OPTS" >> "$LOG"

SPLASHTIME="${SPLASH_TIME:-2}"
if [[ "${OS}" == "win" ]]; then
	CLASSPATH="${JAR_NAME};$(winpath "${LIB_DIR}")/*"
	LD_PATH=$(winpath "${LIB_DIR}")

	# Fixes bug  Unable to get Charset 'cp65001' for property 'sun.stdout.encoding'
	JAVA_OPTS="${JAVA_OPTS} -Dsun.stdout.encoding=UTF-8 -Dsun.err.encoding=UTF-8"
	# shellcheck disable=SC2068
	start "${JAVA_CMD}" --enable-native-access=javafx.graphics,javafx.media,javafx.web \
	-Dsplash.minSeconds=$SPLASHTIME \
	--module-path ${LIB_DIR}/jfx --add-modules ${MODULES}  -Djava.library.path="${LD_PATH}" \
	-cp "${CLASSPATH}" $JAVA_OPTS se.alipsa.gade.Gade
else
	CLASSPATH="${LIB_DIR}/*"
	LD_PATH="${LIB_DIR}"
	echo "JAVA_CMD in env.sh is ${JAVA_CMD}" >> "$LOG"
	echo 'Start Gade' >> "$LOG"
	echo "${JAVA_CMD} --enable-native-access=javafx.graphics,javafx.media,javafx.web \
  -Dsplash.minSeconds=$SPLASHTIME \
  --module-path ${LIB_DIR}/jfx --add-modules ${MODULES} \
  -Djava.library.path=\"${LD_PATH}\" -cp \"${CLASSPATH}\" $JAVA_OPTS se.alipsa.gade.Gade" >> "$LOG"
	# shellcheck disable=SC2068
	${JAVA_CMD} --enable-native-access=javafx.graphics,javafx.media,javafx.web \
	-Dsplash.minSeconds=$SPLASHTIME \
	--module-path ${LIB_DIR}/jfx --add-modules ${MODULES}  \
	-Djava.library.path="${LD_PATH}" -cp "${CLASSPATH}" $JAVA_OPTS se.alipsa.gade.Gade >> "$LOG" 2>&1 &
fi