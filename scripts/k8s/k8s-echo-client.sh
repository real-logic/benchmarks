#!/bin/bash

# Starts the echo client
# Enabled bash job control!
set -emo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
source "${DIR}/k8s-common"

# Do our benchmark pre-setup
f_benchmark_pre

# Starts the process with affinity set to the first core
echo "** Starting with echo client base cpu core ${CGROUP_CPUSETS[1]}"
# Pass in the benchmark client args
taskset -c "${CGROUP_CPUSETS[1]}" \
"${DIR}/../benchmark-runner" \
"$@" aeron/echo-client &

# Wait for the Java process to be up
f_wait_for_process 'uk.co.real_logic.benchmarks.remote.LoadTestRig'

# Sets the affinities for high performance threads
f_pin_thread "load-test-rig" "${CGROUP_CPUSETS[2]}"

# Wait for all background tasks
fg

# Do our post-benchmark work
f_benchmark_post
