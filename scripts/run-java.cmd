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

set JAVA_OPTIONS=^
  -Djava.net.preferIPv4Stack=true ^
  -XX:+UnlockExperimentalVMOptions ^
  -XX:+TrustFinalNonStaticFields ^
  -XX:+UnlockDiagnosticVMOptions ^
  -XX:GuaranteedSafepointInterval=300000 ^
  -XX:+UseParallelGC ^
  -Xms4G ^
  -Xmx4G ^
  -XX:+AlwaysPreTouch ^
  -XX:MaxMetaspaceSize=1G ^
  -XX:ReservedCodeCacheSize=1G ^
  -XX:+PerfDisableSharedMem

FOR /F "tokens=3" %%J IN ('"%JAVA_HOME%\bin\java" -version 2^>^&1 ^| findstr version') DO (
  SET "java_version=%%J"
  SET "java_version=!java_version:"=!"
)

if "!java_version:~0,3!" == "1.8" (
  set "ADD_OPENS="
) else (
  FOR /F "tokens=1 delims=.-" %%A IN ("!java_version!") DO (
    SET major_java_version=%%A
  )

  if !major_java_version! LSS 15 (
    set "JAVA_OPTIONS=!JAVA_OPTIONS! -XX:+UseBiasedLocking -XX:BiasedLockingStartupDelay=0"
  )

  set ADD_OPENS=^
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED ^
  --add-opens java.base/java.util.zip=ALL-UNNAMED
)

"%JAVA_HOME%\bin\java" ^
  -cp "%DIR%\..\benchmarks-all\build\libs\benchmarks.jar" ^
  !JAVA_OPTIONS! ^
  !ADD_OPENS! ^
  %JVM_OPTS% ^
  %*
