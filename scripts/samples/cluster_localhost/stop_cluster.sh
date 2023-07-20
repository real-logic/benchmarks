#!/usr/bin/env bash
##
## Copyright 2015-2023 Real Logic Limited.
##
## Licensed under the Apache License, Version 2.0 (the "License");
## you may not use this file except in compliance with the License.
## You may obtain a copy of the License at
##
## https://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software
## distributed under the License is distributed on an "AS IS" BASIS,
## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
## See the License for the specific language governing permissions and
## limitations under the License.
##

set -euxo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
AERON_SCRIPT_HOME=${DIR}/../../aeron

pkill -f ".*ClusterNode.*" || true
pkill -f ".*MediaDriver.*" || true
rm -rf /dev/shm/node?-driver /dev/shm/client-driver
