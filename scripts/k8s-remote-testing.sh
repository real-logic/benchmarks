#!/bin/bash

set -eo pipefail

function f_show_help() {
  echo "Supported arguments are:"
  echo "${0} (-n|--namespace) '<namespace>' (-t|--test) 'aeron-echo' (-m|--media-driver) 'dpdk'"
}

while [[ $# -gt 0 ]]
do
  option="${1}"
  case ${option} in
    -n|--namespace)
      K8S_NAMESPACE="${2}"
      shift
      shift
      ;;
    -t|--test)
      TEST_TO_RUN="${2}"
      if [[ "${TEST_TO_RUN}" != "aeron-echo" ]]
      then
        echo "Error: only supported test is 'aeron-echo' at the moment"
        exit 1
      fi
      shift
      shift
      ;;
    -m|--media-driver)
      MEDIA_DRIVER="${2}"
      if [[ "${MEDIA_DRIVER}" != "dpdk" ]]
      then
        echo "Error: only supported media driver is 'dpdk' at the moment"
        exit 1
      fi
      shift
      shift
      ;;
    -h|--help)
      f_show_help
      EXIT
      ;;
    *)
      echo "Error, unknown argument: ${option}"
      f_show_help
      exit 1
      ;;
  esac
done

# Standard vars
K8S_NAMESPACE="${K8S_NAMESPACE:-default}"
TEST_TO_RUN="${TEST_TO_RUN:-aeron-echo}"
MEDIA_DRIVER="${MEDIA_DRIVER:-dpdk}"

TIMESTAMP="$(date +"%H-%M-%S_%d-%m-%Y")"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "${SCRIPT_DIR}"

function f_cleanup_k8s() {
  echo "******************************************************************"
  echo "** Deleting old benchmark setup **"
  kubectl -n "${K8S_NAMESPACE}" delete --wait=true -k k8s/ || true
  kubectl -n "${K8S_NAMESPACE}" delete --wait=true endpointslices.discovery.k8s.io/aeron-benchmark-md1 || true
  kubectl -n "${K8S_NAMESPACE}" wait --for=delete endpointslices.discovery.k8s.io/aeron-benchmark-md1 --timeout=60s || true
  kubectl -n "${K8S_NAMESPACE}" wait --for=delete pod/aeron-benchmark-0 --timeout=60s || true
  kubectl -n "${K8S_NAMESPACE}" wait --for=delete pod/aeron-benchmark-1 --timeout=60s || true
}

# Delete the old incarnation of the pods
f_cleanup_k8s

# Generate new test pods
echo "******************************************************************"
echo "** Generating new benchmark setup **"
kubectl -n "${K8S_NAMESPACE}" apply --wait=true -k k8s/
kubectl -n "${K8S_NAMESPACE}" wait --for=condition=Ready pod/aeron-benchmark-0
kubectl -n "${K8S_NAMESPACE}" wait --for=condition=Ready pod/aeron-benchmark-1

# DPDK Media Driver
if [[ "${MEDIA_DRIVER}" == "dpdk" ]]
then
  AB0_MD_IP="$(kubectl -n "${K8S_NAMESPACE}" exec -it aeron-benchmark-0 -c aeronmd-dpdk -- bash -c 'echo ${PCIDEVICE_INTEL_COM_AWS_DPDK_INFO}' | jq -r '.. | ."IPV4_ADDRESS"? | select(. != null)')"
  AB1_MD_IP="$(kubectl -n "${K8S_NAMESPACE}" exec -it aeron-benchmark-1 -c aeronmd-dpdk -- bash -c 'echo ${PCIDEVICE_INTEL_COM_AWS_DPDK_INFO}' | jq -r '.. | ."IPV4_ADDRESS"? | select(. != null)')"
# Java Media Driver
elif [[ "${MEDIA_DRIVER}" == "java" ]]
  then
  AB0_MD_IP="$(kubectl -n "${K8S_NAMESPACE}" get po  aeron-benchmark-0  -o json | jq -r ".status.podIP")"
  AB1_MD_IP="$(kubectl -n "${K8S_NAMESPACE}" get po  aeron-benchmark-1  -o json | jq -r ".status.podIP")"
else
  echo "No available media-driver config"
  exit 1
fi

echo "******************************************************************"
echo "** Found Media driver IPs: **"
echo "aeron-benchmark-0: ${AB0_MD_IP}"
echo "aeron-benchmark-1: ${AB1_MD_IP}"


# Generate endpoint slice with IPs
# Because we can use interfaces that have no obvious IPs, we need to have a way to generate DNS records for the test.
echo "******************************************************************"
echo "** Generating endpointslice with DNS for media driver IPs **"
ENDPOINT_SLICE="
---
apiVersion: discovery.k8s.io/v1
kind: EndpointSlice
metadata:
  name: aeron-benchmark-md1
  labels:
    kubernetes.io/service-name: aeron-benchmark-md
addressType: IPv4
ports:
  # Port/protocol is irrelevant as this is a headless service
  - port: 10000
    name: ''
    protocol: UDP
endpoints:
  - addresses:
      - ${AB0_MD_IP}
    hostname: aeron-benchmark-0
  - addresses:
      - ${AB1_MD_IP}
    hostname: aeron-benchmark-1
"

# Inject endpoint slice
echo "${ENDPOINT_SLICE}" | kubectl -n "${K8S_NAMESPACE}" apply -f -

# When the benchmark finishes, the benchmark containers stop, generating a NotReady condition
echo "******************************************************************"
echo "** Waiting for benchmarks to finish **"
kubectl -n "${K8S_NAMESPACE}" wait --for=condition=Ready=false --timeout=360s pod/aeron-benchmark-0
kubectl -n "${K8S_NAMESPACE}" wait --for=condition=Ready=false --timeout=360s pod/aeron-benchmark-1

echo "******************************************************************"
echo "** Benchmarks finished, showing logs **"
# Show the raw output
kubectl -n "${K8S_NAMESPACE}" logs -c benchmark aeron-benchmark-1

echo "******************************************************************"
echo "** Collecting data **"
mkdir -p "results/${TIMESTAMP}"
# Copy the tarball of results over
kubectl -n "${K8S_NAMESPACE}" cp -c results aeron-benchmark-0:/dev/shm/results.tar.gz "results/${TIMESTAMP}/results-0.tar.gz"
kubectl -n "${K8S_NAMESPACE}" cp -c results aeron-benchmark-1:/dev/shm/results.tar.gz "results/${TIMESTAMP}/results-1.tar.gz"
# Extract the hdr files - if present
tar -C "results/${TIMESTAMP}" --strip-components=1 --wildcards -xvf "results/${TIMESTAMP}/results-0.tar.gz" '*.hdr' || true
tar -C "results/${TIMESTAMP}" --strip-components=1 --wildcards -xvf "results/${TIMESTAMP}/results-1.tar.gz" '*.hdr' || true

# Create an aggregate result & plot it
"${SCRIPT_DIR}/aggregate-results" "results/${TIMESTAMP}"
"${SCRIPT_DIR}/results-plotter.py" "results/${TIMESTAMP}"

echo "******************************************************************"
echo "** Results collected in: ${SCRIPT_DIR}/results/${TIMESTAMP} **"

f_cleanup_k8s
