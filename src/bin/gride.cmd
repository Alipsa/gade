@echo off
set DIR=%~dp0%.
cd %DIR%

set PROPERTY_FILE=version.properties

FOR /F "tokens=1,2 delims==" %%G IN (%PROPERTY_FILE%) DO (set %%G=%%H)

set VERSION=%version%
set JAR_NAME=%jar.name%
set RELEASE_TAG=%release.tag%
set "LIB_DIR=%DIR%\lib"
set "PATH=%PATH%;%LIB_DIR%"

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

start %JAVA_CMD% --module-path %LIB_DIR%/win --add-modules %MODULES% -cp %JAR_NAME% %JAVA_OPTS% se.alipsa.gride.splash.SplashScreen

:: if you dont want the console to remain, do start javaw instead of java

start %JAVA_CMD% --module-path %LIB_DIR%/win --add-modules %MODULES% ^
-Djava.library.path="%LIB_DIR%" -cp %JAR_NAME%;%LIB_DIR%/* ^
%JAVA_OPTS% ^
se.alipsa.gride.Gride
