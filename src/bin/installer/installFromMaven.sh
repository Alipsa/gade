#!/usr/bin/env bash
# This installation script fetches the artifacts from the nexus repos using maven
# It requires
# 1. Java 21 or later installed and on the path
# 2. Maven 3.6.3 or later installed and mvn on the path
# The directory layout and name is due to MacOS requirements but works in Linux and Windows as well

set -e
gadeVersion=1.0.0-SNAPSHOT
appDir=gade.app
mkdir "$appDir"
libDir="$appDir/lib"
mkdir "$libDir"
jfxDir="$libDir/jfx"
mkdir "$jfxDir"
mvn dependency:copy -Dartifact=se.alipsa:gade-runner:$gadeVersion:pom -DoutputDirectory=.
# Copy dependencies to target/dependency
mvn -f gade-runner-$gadeVersion.pom dependency:copy-dependencies -DoutputDirectory="$libDir"
mv "$libDir"/javafx-*.jar "$jfxDir/"
JAR_NAME="gade-$gadeVersion.jar"
# We now copy the gade.sh script so the below can be removed
#MODULES=javafx.controls,javafx.media,javafx.web,javafx.swing
#LD_PATH="${libDir}"
#JAVA=$(command -v java)

#cat > $appDir/gade.sh <<- EOM
##!/usr/bin/env bash
#DIR="\$( cd "\$( dirname "\${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
#if [[ -f \$DIR/env.sh ]]; then
#  source \$DIR/env.sh
#fi
#LIB=\$DIR/lib
#JFX=\$LIB/jfx
#$JAVA --enable-native-access=javafx.graphics,javafx.media,javafx.web --module-path \$JFX --add-modules ${MODULES}  -cp "\$LIB/${JAR_NAME}" \$JAVA_OPTS se.alipsa.gade.splash.SplashScreen &
#$JAVA --enable-native-access=javafx.graphics,javafx.media,javafx.web --module-path \$JFX --add-modules ${MODULES}  -Djava.library.path="\${LD_PATH}" -cp "\$LIB/*" \$JAVA_OPTS se.alipsa.gade.Gade &
#EOM
#chmod +x $appDir/gade.sh
## End of removable code block

unzip $libDir/$JAR_NAME 'mac/*' -d "$appDir"
mv "$appDir/mac/Contents" "$appDir/Contents"
rm -r "$appDir/mac"

unzip $libDir/$JAR_NAME 'script/*' -d "$appDir"
cp "$appDir"/script/* "$appDir/"
rm -r "$appDir/script"

# Cleanup
rm gade-runner*.pom