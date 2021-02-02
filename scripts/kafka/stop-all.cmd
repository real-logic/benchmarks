::
:: Copyright 2015-2021 Real Logic Limited.
::
:: Licensed under the Apache License, Version 2.0 (the "License");
:: you may not use this file except in compliance with the License.
:: You may obtain a copy of the License at
::
:: https://www.apache.org/licenses/LICENSE-2.0
::
:: Unless required by applicable law or agreed to in writing, software
:: distributed under the License is distributed on an "AS IS" BASIS,
:: WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
:: See the License for the specific language governing permissions and
:: limitations under the License.
::
@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "DIR=%~dp0"

rem Kill Kafka process
call :killJavaProcess "kafka.Kafka"
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

rem Kill Zookeeper process
call :killJavaProcess "org.apache.zookeeper.server.quorum.QuorumPeerMain"
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

rem Finally delete the data directory
if "%KAFKA_DATA_DIR%" == "" (
  set "KAFKA_DATA_DIR=%DIR%\..\kafka-data"
)

if exist "%KAFKA_DATA_DIR%" (
  rmdir /S /Q "%KAFKA_DATA_DIR%"
)

goto :eof

:killJavaProcess
SET name=%1
FOR /F "tokens=* USEBACKQ" %%P IN (`tasklist /FI "ImageName eq java.exe" /SVC /FO csv /NH`) DO (
  SET "pid=%%P"
  if not "!pid:~0,5!" == "INFO:" (
    SET "pid=!pid:"java.exe","=!"
    SET "pid=!pid:","N/A"=!"

    FOR /F "tokens=* USEBACKQ" %%J IN (`%JAVA_HOME%\bin\jcmd !pid! VM.command_line ^| findstr %name%`) DO (
      SET TASK_PID=!pid!
    )
  )
)

if [!TASK_PID!] == [] (
  echo %name% is not running
) else (
  taskkill /T /F /PID !TASK_PID!
  if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%
  timeout /t 1 >NUL
)

exit /b 0