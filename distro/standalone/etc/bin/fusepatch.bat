@echo off

@if not "%ECHO%" == ""  echo %ECHO%
@if "%OS%" == "Windows_NT" setlocal

if "%OS%" == "Windows_NT" (
  set "DIRNAME=%~dp0%"
) else (
  set DIRNAME=..\
)

set "HOME_DIR=%DIRNAME%..\"

pushd "%DIRNAME%.."
set "RESOLVED_JBOSS_HOME=%CD%"
popd

java -jar ^
    -Dlog4j.configuration=file:%HOME_DIR%\config\logging.properties ^
    -Dfusepatch.repository=file:%HOME_DIR%\repository ^
    %HOME_DIR%/lib/fuse-patch-core-${project.version}.jar %*
