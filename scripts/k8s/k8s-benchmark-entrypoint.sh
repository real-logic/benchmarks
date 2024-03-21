#!/bin/bash

set -eo pipefail

echo '***********************************'
echo "*** Running $* on ${HOSTNAME} ***"
echo '***********************************'

# Create the results dir
mkdir -p "${TEST_OUTPUT_PATH:? Please set TEST_OUTPUT_PATH}"
cd "${BENCHMARKS_PATH:? Please set BENCHMARKS_PATH}/scripts"

# Collecting environment info
./collect-environment-info "${TEST_OUTPUT_PATH}"

# Verify we can get DNS records for the pods
until host "${NODE0_ADDRESS:? Please set NODE0_ADDRESS}"
do
  echo "waiting for DNS for ${NODE0_ADDRESS:? Please set NODE0_ADDRESS}"
  sleep 5
done
until host "${NODE1_ADDRESS:? Please set NODE1_ADDRESS}"
do
  echo "waiting for DNS for ${NODE1_ADDRESS:? Please set NODE1_ADDRESS}"
  sleep 5
done

echo '*******************************'
echo "JVM_OPTS:"
echo "${JVM_OPTS:? Please set JVM_OPTS}" | sed "s/ /\n/g"
echo '*******************************'
# Run our command args
"$@"

# Tar results if we've got them
if [ -z "$(ls -A ${TEST_OUTPUT_PATH})" ]; then
   echo "No results found"
else
   parent_dir="$(dirname "${TEST_OUTPUT_PATH}")"
   results_dir="$(basename "${TEST_OUTPUT_PATH}")"
   echo "Generating summary"
   "${BENCHMARKS_PATH}/scripts/aggregate-results" "${TEST_OUTPUT_PATH}"
   echo "Creating results tarball: ${parent_dir}/results.tar.gz"
   tar -C "${parent_dir}" -czf "${parent_dir}/results.tar.gz" "${results_dir}"
fi
