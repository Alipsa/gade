@echo off
set DIR=%~dp0%.
cd %DIR%

set PROPERTY_FILE=version.properties

FOR /F "tokens=1,2 delims==" %%G IN (%PROPERTY_FILE%) DO (set %%G=%%H)

set VERSION=%version%
set JAR_NAME=%jar.name%
set RELEASE_TAG=%release.tag%
set "LIB_DIR=%DIR%\lib"
set "BIN_DIR=%DIR%\bin"
set "PATH=%PATH%;%LIB_DIR%"

:: Allow for any kind of customization of variables or paths etc. without having to change this script
:: which would otherwise be overwritten on a subsequent install.
if exist %DIR%\env.cmd (
    call %DIR%\env.cmd
)

SET JAVA_HOME=%DIR%
if not defined JAVA_CMD (
	set JAVA_CMD=javaw
)

start %BIN_DIR%\%JAVA_CMD% -cp %LIB_DIR%/%JAR_NAME% %JAVA_OPTS% se.alipsa.gade.splash.SplashScreen

:: if you dont want the console to remain, do start javaw instead of java

start %BIN_DIR%\%JAVA_CMD% ^
-Djava.library.path="%LIB_DIR%" -cp %JAR_NAME%;%LIB_DIR%/* ^
%JAVA_OPTS% ^
se.alipsa.gade.Gade
