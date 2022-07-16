#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "${DIR}" || exit 1

LAUNCHER=~/.local/share/applications/grade.desktop
{
echo "[Desktop Entry]
Name=Grade
Exec=${DIR}/grade.sh
Comment=Grade, a Groovy Analytics Development Environment
Terminal=false
Icon=${DIR}/grade-icon.png
Type=Application
Categories=Development"
} > ${LAUNCHER}

chmod +x grade.sh
chmod +x ${LAUNCHER}
if [[ -f ~/Desktop/grade.desktop ]]; then
  rm ~/Desktop/grade.desktop
fi
ln -s ${LAUNCHER} ~/Desktop/grade.desktop

echo "Launcher shortcuts created!"