#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "${DIR}" || exit 1

LAUNCHER=~/.local/share/applications/ride.desktop
{
echo "[Desktop Entry]
Name=Gride
Exec=${DIR}/gride.sh
Comment=Gride, a Groovy IDE for Data Science
Terminal=false
Icon=${DIR}/gride-icon.png
Type=Application
Categories=Development"
} > ${LAUNCHER}

chmod +x gride.sh
chmod +x ${LAUNCHER}
ln -s ${LAUNCHER} ~/Desktop/gride.desktop

echo "Launcher shortcuts created!"