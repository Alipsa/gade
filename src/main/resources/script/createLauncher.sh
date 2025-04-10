#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "${DIR}" || exit 1

SHORTCUT_NAME=gade.desktop
LAUNCHER=~/.local/share/applications/$SHORTCUT_NAME
{
echo "[Desktop Entry]
Name=Gade
Exec=${DIR}/gade.sh
Comment=Gade, a Groovy Analytics Development Environment
Terminal=false
Icon=${DIR}/gade-icon.png
Type=Application
Categories=Development"
} > ${LAUNCHER}

chmod +x gade.sh
chmod +x ${LAUNCHER}
if [[ -f ~/Desktop/$SHORTCUT_NAME ]]; then
  rm ~/Desktop/$SHORTCUT_NAME
fi
ln -s ${LAUNCHER} ~/Desktop/$SHORTCUT_NAME

echo "Launcher shortcuts created!"