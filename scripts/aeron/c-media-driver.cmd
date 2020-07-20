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

set "DIR=%~dp0"

if [%AERON_MD%] == [] (
  set "AERON_MD=%DIR%\..\..\cppbuild\Release\aeron-prefix\src\aeron-build\binaries\aeronmd"
)

echo "Starting C MediaDriver '%AERON_MD%' ..."

%AERON_MD% ^
  "%DIR%\low-latency-driver.properties" ^
  "%DIR%\benchmark.properties" ^
  %*
