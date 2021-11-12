DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"

export SSH_SERVER_NODE=dpdk-1
export SSH_KEY_FILE=
export SSH_USER=
export SSH_CLIENT_NODE=dpdk-4
export SSH_CLUSTER_NODE0=dpdk-1
export SSH_CLUSTER_NODE1=dpdk-2
export SSH_CLUSTER_NODE2=dpdk-3
CLIENT_NODE_IP=
NODE0_IP=
NODE1_IP=
NODE2_IP=
INTERFACE=
CONSENSUS_CHANNEL="aeron:udp?term-length=64k|interface=${INTERFACE}"

#SOURCE_DIR="/home/${SSH_USER}/dev"
#JAVA_HOME="${SOURCE_DIR}/zulu8.54.0.21-ca-jdk8.0.292-linux_x64"
JAVA_HOME="/home/${SSH_USER}/java"
BENCHMARKS_PATH="/home/${SSH_USER}/aeron_benchmarks"
DATA_DIR="/home/${SSH_USER}/data"

export CLIENT_JAVA_HOME="${JAVA_HOME}"
export CLIENT_BENCHMARKS_PATH="${BENCHMARKS_PATH}"
export CLIENT_DRIVER_NUMA_NODE=0
export CLIENT_LOAD_TEST_RIG_NUMA_NODE=0
export CLIENT_EGRESS_CHANNEL="aeron:udp?endpoint=${CLIENT_NODE_IP}:0|interface=${INTERFACE}"
export CLIENT_INGRESS_CHANNEL="aeron:udp?interface=${INTERFACE}"
export CLIENT_INGRESS_ENDPOINTS="0=${NODE0_IP}:20000,1=${NODE1_IP}:21000,2=${NODE2_IP}:22000"

export CLUSTER_SIZE=3
export CLUSTER_MEMBERS="0,${NODE0_IP}:20000,${NODE0_IP}:20001,${NODE0_IP}:20002,${NODE0_IP}:20003,${NODE0_IP}:20004|\
1,${NODE1_IP}:21000,${NODE1_IP}:21001,${NODE1_IP}:21002,${NODE1_IP}:21003,${NODE1_IP}:21004|\
2,${NODE2_IP}:22000,${NODE2_IP}:22001,${NODE2_IP}:22002,${NODE2_IP}:22003,${NODE2_IP}:22004"

export NODE0_JAVA_HOME="${JAVA_HOME}"
export NODE0_BENCHMARKS_PATH="${BENCHMARKS_PATH}"
export NODE0_DRIVER_NUMA_NODE=0
export NODE0_NUMA_NODE=0
export NODE0_CLUSTER_DIR="${DATA_DIR}/cluster"
export NODE0_CLUSTER_CONSENSUS_CHANNEL="${CONSENSUS_CHANNEL}"
export NODE0_CLUSTER_INGRESS_CHANNEL="aeron:udp?interface=${INTERFACE}"
export NODE0_CLUSTER_LOG_CHANNEL="aeron:udp?term-length=64m|control-mode=manual|control=${NODE0_IP}:20002|interface=${INTERFACE}"
export NODE0_CLUSTER_REPLICATION_CHANNEL="aeron:udp?endpoint=${NODE0_IP}:0|interface=${INTERFACE}"
export NODE0_ARCHIVE_DIR="${DATA_DIR}/archive"
export NODE0_ARCHIVE_CONTROL_CHANNEL="aeron:udp?endpoint=${NODE0_IP}:8010|interface=${INTERFACE}"

export NODE1_JAVA_HOME="${JAVA_HOME}"
export NODE1_BENCHMARKS_PATH="${BENCHMARKS_PATH}"
export NODE1_DRIVER_NUMA_NODE=0
export NODE1_NUMA_NODE=0
export NODE1_CLUSTER_DIR="${DATA_DIR}/cluster"
export NODE1_CLUSTER_CONSENSUS_CHANNEL="${CONSENSUS_CHANNEL}"
export NODE1_CLUSTER_INGRESS_CHANNEL="aeron:udp?interface=${INTERFACE}"
export NODE1_CLUSTER_LOG_CHANNEL="aeron:udp?term-length=64m|control-mode=manual|control=${NODE1_IP}:21002|interface=${INTERFACE}"
export NODE1_CLUSTER_REPLICATION_CHANNEL="aeron:udp?endpoint=${NODE1_IP}:0|interface=${INTERFACE}"
export NODE1_ARCHIVE_DIR="${DATA_DIR}/archive"
export NODE1_ARCHIVE_CONTROL_CHANNEL="aeron:udp?endpoint=${NODE1_IP}:8010|interface=${INTERFACE}"

export NODE2_JAVA_HOME="${JAVA_HOME}"
export NODE2_BENCHMARKS_PATH="${BENCHMARKS_PATH}"
export NODE2_DRIVER_NUMA_NODE=0
export NODE2_NUMA_NODE=0
export NODE2_CLUSTER_DIR="${DATA_DIR}/cluster"
export NODE2_CLUSTER_CONSENSUS_CHANNEL="${CONSENSUS_CHANNEL}"
export NODE2_CLUSTER_INGRESS_CHANNEL="aeron:udp?interface=${INTERFACE}"
export NODE2_CLUSTER_LOG_CHANNEL="aeron:udp?term-length=64m|control-mode=manual|control=${NODE2_IP}:22002|interface=${INTERFACE}"
export NODE2_CLUSTER_REPLICATION_CHANNEL="aeron:udp?endpoint=${NODE2_IP}:0|interface=${INTERFACE}"
export NODE2_ARCHIVE_DIR="${DATA_DIR}/archive"
export NODE2_ARCHIVE_CONTROL_CHANNEL="aeron:udp?endpoint=${NODE2_IP}:8010|interface=${INTERFACE}"

export MESSAGE_RATE=100
export MESSAGE_LENGTH=32

time "${DIR}/aeron/remote-cluster-benchmarks" --no-c-driver --no-ats --no-ef_vi --no-onload --file-sync-level 0