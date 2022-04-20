#!/usr/bin/env bash
##
## Copyright 2015-2022 Real Logic Limited.
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

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
AERON_SCRIPT_HOME=${DIR}/../../aeron

export JVM_OPTS="-Xms16M"

echo "Starting media drivers"
${AERON_SCRIPT_HOME}/media-driver ${DIR}/cluster.properties ${DIR}/node0.properties > md_node0.out &
${AERON_SCRIPT_HOME}/media-driver ${DIR}/cluster.properties ${DIR}/node1.properties > md_node1.out &
${AERON_SCRIPT_HOME}/media-driver ${DIR}/cluster.properties ${DIR}/node2.properties > md_node2.out &
${AERON_SCRIPT_HOME}/media-driver ${DIR}/cluster.properties ${DIR}/client.properties > md_client.out &

echo "Cluster nodes"
${AERON_SCRIPT_HOME}/cluster-node ${DIR}/cluster.properties ${DIR}/node0.properties > node0.out &
${AERON_SCRIPT_HOME}/cluster-node ${DIR}/cluster.properties ${DIR}/node1.properties > node1.out &
${AERON_SCRIPT_HOME}/cluster-node ${DIR}/cluster.properties ${DIR}/node2.properties > node2.out &

echo "Start client"
${AERON_SCRIPT_HOME}/cluster-client ${DIR}/cluster.properties ${DIR}/node2.properties