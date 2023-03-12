#!/usr/bin/env bash
# rm -rf ~/.groovy/grapes/se.alipsa.groovy/data-utils/
echo "Clearing cache of data-utils"
grape uninstall se.alipsa.groovy data-utils 1.0.3-SNAPSHOT
echo "Clearing cache of matrix"
grape uninstall se.alipsa matrix 1.0-SNAPSHOT