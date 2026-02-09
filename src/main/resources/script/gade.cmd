@echo off
set DIR=%~dp0%.
cd %DIR%

set "LIB_DIR=%DIR%\lib"
set "PATH=%PATH%;%LIB_DIR%"

set "jars=gade-*.jar"
pushd %LIB_DIR%
for %%a in (%jars%) do set JAR_NAME=%%a
popd

:: Allow for any kind of customization of variables or paths etc. without having to change this script
:: which would otherwise be overwritten on a subsequent install.
if exist %DIR%\env.cmd (
    call %DIR%\env.cmd
)

if not defined JAVA_CMD (
	if defined JAVA_HOME (
		:: check if trailing backslash
		IF %JAVA_HOME:~-1%==\ (
			set JAVA_CMD=%JAVA_HOME%bin\javaw
		) else (
			set JAVA_CMD=%JAVA_HOME%\bin\javaw
		)
	) else (
		set JAVA_CMD=javaw
	)
)

if not defined SPLASHTIME (
	set SPLASHTIME=2
)

set MODULES=javafx.controls,javafx.media,javafx.web,javafx.swing

:: if you dont want the console to remain, do start javaw instead of java

start %JAVA_CMD% --enable-native-access=javafx.graphics,javafx.media,javafx.web ^
-Dsplash.minSeconds=%SPLASHTIME% ^
--module-path %LIB_DIR%\jfx --add-modules %MODULES% ^
-Djava.library.path="%LIB_DIR%" -cp %LIB_DIR%\* %JAVA_OPTS% ^
se.alipsa.gade.Gade
