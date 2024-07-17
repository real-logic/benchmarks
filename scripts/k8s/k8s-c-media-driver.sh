#!/bin/bash

# Starts the java media driver
# Enabled bash job control!
set -emo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
source "${DIR}/k8s-common"

# Sets our own bash script affinity to the first core
taskset -cp "${CGROUP_CPUSETS[0]}" $$

# Write the customised benchmark properties
f_generate_benchmark_properties

# Starts the media driver with affinity set to the first core
echo "** Starting with base cpu core ${CGROUP_CPUSETS[0]}"
"${DIR}/../aeron/c-media-driver" &

# Wait for all background tasks
fg
