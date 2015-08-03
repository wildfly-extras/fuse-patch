@echo off
@if not "%ECHO%" == ""  echo %ECHO%
@if "%OS%" == "Windows_NT" setlocal

if "%OS%" == "Windows_NT" (
  set "DIRNAME=%~dp0%"
) else (
  set DIRNAME=.\
)

pushd "%DIRNAME%.."
set "RESOLVED_JBOSS_HOME=%CD%"
popd

if "x%JBOSS_HOME%" == "x" (
  set "JBOSS_HOME=%RESOLVED_JBOSS_HOME%"
)

pushd "%JBOSS_HOME%"
set "SANITIZED_JBOSS_HOME=%CD%"
popd

if /i "%RESOLVED_JBOSS_HOME%" NEQ "%SANITIZED_JBOSS_HOME%" (
   echo.
   echo   WARNING: The JBOSS_HOME ^("%SANITIZED_JBOSS_HOME%"^) that this script uses points to a different installation than the one that this script resides in ^("%RESOLVED_JBOSS_HOME%"^). Unpredictable results may occur.
   echo.
   echo       JBOSS_HOME: "%JBOSS_HOME%"
   echo.
)

if "x%JAVA_HOME%" == "x" (
  set  JAVA=java
  echo JAVA_HOME is not set. Unexpected results may occur.
  echo Set JAVA_HOME to the directory of your local JDK to avoid this message.
) else (
  set "JAVA=%JAVA_HOME%\bin\java"
)

if exist "%JBOSS_HOME%\jboss-modules.jar" (
    set "RUNJAR=%JBOSS_HOME%\jboss-modules.jar"
) else (
  echo Could not locate "%JBOSS_HOME%\jboss-modules.jar".
  echo Please check that you are in the bin directory when running this script.
  goto END
)

if "x%JBOSS_MODULEPATH%" == "x" (
  set "JBOSS_MODULEPATH=%JBOSS_HOME%\modules"
)

set "FUSEPATCH_OPTS=%TMP%\fusepatchopts.txt"
echo %JAVA_OPTS% > %FUSEPATCH_OPTS%
findstr /m "logging.configuration" %FUSEPATCH_OPTS%
if %errorlevel%==0 (
"%JAVA%" %JAVA_OPTS% ^
    -jar "%JBOSS_HOME%\jboss-modules.jar" ^
    -mp "%JBOSS_MODULEPATH%" ^
     org.wildfly.extras.patch ^
     %*
) else (
"%JAVA%" %JAVA_OPTS% ^
    -Dlogging.configuration="file:%JBOSS_HOME%\bin\fusepatch-logging.properties" ^
    -jar "%JBOSS_HOME%\jboss-modules.jar" ^
    -mp "%JBOSS_MODULEPATH%" ^
     org.wildfly.extras.patch ^
     %*
)

del /F %FUSEPATCH_OPTS%
