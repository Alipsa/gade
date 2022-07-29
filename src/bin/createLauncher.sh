#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "${DIR}" || exit 1

LAUNCHER=~/.local/share/applications/gade.desktop
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
if [[ -f ~/Desktop/gade.desktop ]]; then
  rm ~/Desktop/gade.desktop
fi
ln -s ${LAUNCHER} ~/Desktop/gade.desktop

echo "Launcher shortcuts created!"