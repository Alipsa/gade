@echo off
set DIR=%~dp0%.
cd %DIR%

set "LIB_DIR=%DIR%\lib"
set "PATH=%PATH%;%LIB_DIR%"

setlocal
set "jars=gade-*.jar"
pushd %LIB_DIR%
for %%a in (%jars%) do set JAR_NAME=%%a
popd
endlocal

:: Allow for any kind of customization of variables or paths etc. without having to change this script
:: which would otherwise be overwritten on a subsequent install.
if exist %DIR%\env.cmd (
    call %DIR%\env.cmd
)

if defined JAVA_HOME (
	set JAVA_CMD=%JAVA_HOME%\bin\javaw
) else (
	set JAVA_CMD=javaw
)

set MODULES=javafx.controls,javafx.media,javafx.web,javafx.swing

start %JAVA_CMD% --enable-native-access=javafx.graphics,javafx.media,javafx.web ^
--module-path %LIB_DIR%/jfx --add-modules %MODULES% -cp %LIB_DIR%/%JAR_NAME% %JAVA_OPTS% ^
se.alipsa.gade.splash.SplashScreen

:: if you dont want the console to remain, do start javaw instead of java

start %JAVA_CMD% --enable-native-access=javafx.graphics,javafx.media,javafx.web ^
--module-path %LIB_DIR%/jfx --add-modules %MODULES% ^
-Djava.library.path="%LIB_DIR%" -cp %LIB_DIR%/* %JAVA_OPTS% ^
se.alipsa.gade.Gade
