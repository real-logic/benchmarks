#!/bin/bash

# Starts the java media driver
# Enabled bash job control!
set -emo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
source "${DIR}/k8s-common"

# Sets our own bash script affinity to the first core
taskset -cp "${CGROUP_CPUSETS[0]}" $$

# Starts the media driver with affinity set to the first core
echo "** Starting with base cpu core ${CGROUP_CPUSETS[0]}"
taskset -c "${CGROUP_CPUSETS[0]}" "${DIR}/../aeron/media-driver" &

# Wait for Java process to be up
f_wait_for_process 'io.aeron.driver.MediaDriver'

# Sets the affinities for the rest of the threads
f_pin_thread "driver-conducto" "${CGROUP_CPUSETS[1]}"
f_pin_thread "sender" "${CGROUP_CPUSETS[2]}"
f_pin_thread "receiver" "${CGROUP_CPUSETS[3]}"

# Wait for all background tasks
fg
