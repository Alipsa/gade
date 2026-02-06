@echo off
set DIR=%~dp0%.
cd %DIR%

set "LIB_DIR=%DIR%\lib"
set "PATH=%PATH%;%LIB_DIR%"

setlocal
set "jars=gade-*.jar"
pushd %LIB_DIR%\app
for %%a in (%jars%) do set JAR_NAME=%%a
popd
endlocal

:: Allow for any kind of customization of variables or paths etc. without having to change this script
:: which would otherwise be overwritten on a subsequent install.
if exist %DIR%\env.cmd (
    call %DIR%\env.cmd
)

:: Use the bundles version of java
SET JAVA_HOME=%DIR%

:: if you dont want the console to remain, do start javaw instead of java
if not defined JAVA_CMD (
	set JAVA_CMD=javaw
)

set MODULES=javafx.controls,javafx.media,javafx.web,javafx.swing
set JAVA_OPTS="%JAVA_OPTS% -Djava.security.auth.login.config=$DIR\conf\jaas.conf"

start %BIN_DIR%\%JAVA_CMD% ^
%JAVA_OPTS% ^
--enable-native-access=javafx.graphics,javafx.media,javafx.web,ALL-UNNAMED ^
--module-path %LIB_DIR%/win --add-modules %MODULES% ^
-cp %LIB_DIR%\app\*;%LIB_DIR%\groovy\* se.alipsa.gade.splash.SplashScreen

start %BIN_DIR%\%JAVA_CMD% ^
%JAVA_OPTS% ^
--enable-native-access=javafx.graphics,javafx.media,javafx.web,ALL-UNNAMED ^
--add-opens=java.base/java.lang=ALL-UNNAMED ^
--add-opens=java.base/java.util=ALL-UNNAMED ^
--add-opens=java.base/java.io=ALL-UNNAMED ^
--add-opens=java.base/java.net=ALL-UNNAMED ^
-Djava.library.path="%LIB_DIR%" ^
--module-path %LIB_DIR%/win --add-modules %MODULES% ^
-cp %LIB_DIR%\app\*;%LIB_DIR%\groovy\* ^
se.alipsa.gade.Gade
