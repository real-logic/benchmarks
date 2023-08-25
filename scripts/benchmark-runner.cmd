::
:: Copyright 2015-2023 Real Logic Limited.
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

rem
rem Example: Invoking `benchmark-runner` from the `scripts` directory.
rem
rem $ benchmark-runner --output-file "echo-test" --message-rate "1000,5000" --burst-size "1,10" --message-length "32,288,1344" "aeron\echo-client"
rem

set ITERATIONS=30
set MESSAGE_RATE=501K
set BURST_SIZE=1
set MESSAGE_LENGTH="288

:loop
if not "%1"=="" (
  set "FLAG="
  if "%1"=="--output-file" set FLAG=1
  if "%1"=="-o" set FLAG=1
  if defined FLAG (
      set "OUTPUT_FILE_NAME=%2";
  )

  set "FLAG="
  if "%1"=="--message-rate" set FLAG=1
  if "%1"=="-m" set FLAG=1
  if defined FLAG (
      set "MESSAGE_RATE=%2";
  )

  set "FLAG="
  if "%1"=="--burst-size" set FLAG=1
  if "%1"=="-b" set FLAG=1
  if defined FLAG (
      set "BURST_SIZE=%2";
  )

  set "FLAG="
  if "%1"=="--message-length" set FLAG=1
  if "%1"=="-l" set FLAG=1
  if defined FLAG (
      set "MESSAGE_LENGTH=%2";
  )

  set "FLAG="
  if "%1"=="--iterations" set FLAG=1
  if "%1"=="-i" set FLAG=1
  if defined FLAG (
      set "ITERATIONS=%2";
  )

  set "FLAG="
  if "%1"=="--help" set FLAG=1
  if "%1"=="-h" set FLAG=1
  if defined FLAG (
      echo "%0 (-o|--output-file) ^"\${output-file-name-prefix}\" [(-m|--message-rate) ^"\${message-rate}\"] [(-b|--burst-size) ^"\${burst-size}\"] [(-l|--message-length) ^"\${message-length}\"] [(-i|--iterations) ^${iterations}] ^"\${command} ^${cmdArg1} ...\""
      exit /b
  )

  set "COMMAND=%1"
  shift
  goto :loop
)

if [%OUTPUT_FILE_NAME%] == [] (
  echo 'Flag -o/--output-file is required'
  exit -1
)

set JVM_OPTS=%JVM_OPTS% ^
-Duk.co.real_logic.benchmarks.remote.output.file=%OUTPUT_FILE_NAME% ^
-Duk.co.real_logic.benchmarks.remote.iterations=%ITERATIONS% ^
-Duk.co.real_logic.benchmarks.remote.message.rate=%MESSAGE_RATE% ^
-Duk.co.real_logic.benchmarks.remote.batch.size=%BURST_SIZE% ^
-Duk.co.real_logic.benchmarks.remote.message.length=%MESSAGE_LENGTH%

 %COMMAND%
