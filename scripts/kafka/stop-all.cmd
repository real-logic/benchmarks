::
:: Copyright 2015-2020 Real Logic Limited.
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

rem Kill Kafka process
call :killJavaProcess Kafka
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

rem Kill Zookeeper process
call :killJavaProcess QuorumPeerMain
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

rem Finally delete the data directory
set "DATA_DIR=%CD%\..\kafka-data"
if exist "%DATA_DIR%" (
  rmdir /S /Q %DATA_DIR%
)

goto :eof

:killJavaProcess
SET name=%1
FOR /F "tokens=* USEBACKQ" %%F IN (`"%JAVA_HOME%\bin\jps" ^| findstr %name%`) DO (
  SET TASK_PID=%%F
  SET TASK_PID=!TASK_PID:%name%=!
)

if [!TASK_PID!] == [] (
  echo %name% is not running
) else (
  taskkill /T /F /PID !TASK_PID!
  if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%
)

exit /b 0