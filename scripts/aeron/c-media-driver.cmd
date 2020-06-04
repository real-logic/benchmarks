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

set AERON_DIR_DELETE_ON_START="1"
set AERON_DIR_DELETE_ON_SHUTDOWN="1"
set AERON_TERM_BUFFER_SPARSE_FILE="0"
set AERON_SOCKET_SO_RCVBUF="2m"
set AERON_SOCKET_SO_SNDBUF="2m"
set AERON_RCV_INITIAL_WINDOW_LENGTH="2m"
set AERON_PUBLICATION_TERM_WINDOW_LENGTH="2m"
set AERON_THREADING_MODE="DEDICATED"
set AERON_CONDUCTOR_IDLE_STRATEGY="spinning"
set AERON_SENDER_IDLE_STRATEGY="noop"
set AERON_RECEIVER_IDLE_STRATEGY="noop"

if [%AERON_MD%] == [] (
  set "AERON_MD=..\..\cppbuild\Release\aeron-prefix\src\aeron-build\binaries\aeronmd"
)

echo "Starting C MediaDriver '%AERON_MD%' ..."
%AERON_MD%
