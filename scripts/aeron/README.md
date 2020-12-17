Overview
--------

This directory provides scripts to benchmark Aeron using the `uk.co.real_logic.benchmarks.remote.LoadTestRig`.

All benchmarks require two nodes: "client" and "server", where "client" is the
`uk.co.real_logic.benchmarks.remote.LoadTestRig` that uses one of the
`uk.co.real_logic.benchmarks.remote.MessageTransceiver`implementations under the hood, and the "server" is the
remote node that pipes messages through.

NOTE: It is advised to have "client" and "server" run on different machines.

Test scenarios
--------------

Three different test scenarios are covered:
1. Echo benchmark (aka ping-pong)

    Start the scripts in the following order: `echo-server` -> `echo-client`.

1. Live replay from remote archive
    
    The client publishes messages to the server using publication over UDP. The server pipes those messages into a
    local IPC publication which records them into an archive. Finally, the client subscribes to the replay from that
    archive over UDP.
    
    Start the scripts in the following order: `live-replay-remote-archive-server` -> `live-replay-remote-archive-client`.

1. Live recording, i.e. client runs records a publication into local archive
    
    The client publishes messages over UDP to the server. It also has a recording running on that publication using
    local archive. The server simply pipes message back. Finally, the client performs a controlled poll on the
    subscription from the server limited by the "recording progress" which it gets via the recording events.
    
    The biggest difference between scenario 2 and this scenario is that there is no replay of recorded message and hence
    no reading from the disc of the saved data but still allowing consumption of those messages that were successfully
    persisted.
    
    Start the scripts in the following order: `echo-server` -> `live-recording-client`.

1. Echo cluster benchmark

   Similar to the echo benchmark but the messages are sent to the cluster using
   `io.aeron.cluster.client.AeronCluster.tryClaim` and received from the cluster using
   `io.aeron.cluster.client.AeronCluster.pollEgress` API.
   
   Start the scripts in the following order: `cluster-node` ... -> `cluster-client`.

   For each cluster node the following properties must be configured:
   - `aeron.dir` - a dedicated directory for the `MediaDriver`.
   - `aeron.archive.dir` - a dedicated directory for the `Archive`.
   - `aeron.cluster.dir` - a dedicated directory for the `ConsensusModule`.
   - `aeron.cluster.members` - the static list of cluster nodes.
   - `aeron.cluster.member.id` - unique ID of the cluster node, must be one of the IDs in the cluster members list.
   - `aeron.archive.control.channel` - `Archive` control channel, must match the last part of the corresponding cluster
     members entry.
   - `aeron.archive.control.stream.id` - `Archive` control channel stream ID, must be unique across cluster nodes on
     the same machine.
   - `aeron.cluster.log.channel` - log channel for distributing cluster log. Usually configured as multicast or as
     multi destination cast. Must match the third part of the corresponding cluster members entry.
   - `aeron.cluster.ingress.channel` - an ingress channel to which messages to the cluster will be sent. Can be
     configured as multicast for efficiency. Must match the first part of the corresponding cluster members entry.
   - `aeron.cluster.ingress.endpoints` - a list of ingress endpoints which will be substituted into
     `aeron.cluster.ingress.channel` for endpoints. Should not be set if the `aeron.cluster.ingress.channel` is
     multicast. This is client-only property.
     
   Here is a sample config for a three node cluster benchmark:
   ```bash
    # node 0
    aeron.dir=/dev/shm/node0-driver
    aeron.archive.dir=/tmp/node0-archive
    aeron.cluster.dir=/tmp/node0-cluster
    aeron.cluster.members=0,localhost:20000,localhost:20001,localhost:20002,localhost:20003,localhost:20004|\
    1,localhost:21000,localhost:21001,localhost:21002,localhost:21003,localhost:21004|\
    2,localhost:22000,localhost:22001,localhost:22002,localhost:22003,localhost:22004
    aeron.cluster.member.id=0
    aeron.archive.control.channel=aeron:udp?endpoint=localhost:20004
    aeron.archive.control.stream.id=100
    aeron.archive.recording.events.enabled=false
    aeron.cluster.log.channel=aeron:udp?control-mode=manual|control=localhost:20002
    aeron.cluster.ingress.channel=aeron:udp
    
    # node 1
    aeron.dir=/dev/shm/node1-driver
    aeron.archive.dir=/tmp/node1-archive
    aeron.cluster.dir=/tmp/node1-cluster
    aeron.cluster.members=0,localhost:20000,localhost:20001,localhost:20002,localhost:20003,localhost:20004|\
    1,localhost:21000,localhost:21001,localhost:21002,localhost:21003,localhost:21004|\
    2,localhost:22000,localhost:22001,localhost:22002,localhost:22003,localhost:22004
    aeron.cluster.member.id=1
    aeron.archive.control.channel=aeron:udp?endpoint=localhost:21004
    aeron.archive.control.stream.id=101
    aeron.archive.recording.events.enabled=false
    aeron.cluster.log.channel=aeron:udp?control-mode=manual|control=localhost:21002
    aeron.cluster.ingress.channel=aeron:udp
    
    # node2
    aeron.dir=/dev/shm/node2-driver
    aeron.archive.dir=/tmp/node2-archive
    aeron.cluster.dir=/tmp/node2-cluster
    aeron.cluster.members=0,localhost:20000,localhost:20001,localhost:20002,localhost:20003,localhost:20004|\
    1,localhost:21000,localhost:21001,localhost:21002,localhost:21003,localhost:21004|\
    2,localhost:22000,localhost:22001,localhost:22002,localhost:22003,localhost:22004
    aeron.cluster.member.id=2
    aeron.archive.control.channel=aeron:udp?endpoint=localhost:22004
    aeron.archive.control.stream.id=102
    aeron.archive.recording.events.enabled=false
    aeron.cluster.log.channel=aeron:udp?control-mode=manual|control=localhost:22002
    aeron.cluster.ingress.channel=aeron:udp
    
    # client
    aeron.cluster.ingress.channel=aeron:udp
    aeron.cluster.ingress.endpoints=0=localhost:20000,1=localhost:21000,2=localhost:22000
   ```
    

Helper scripts
--------------

Besides the scripts to run the client and server for each case, there are several helper scripts:
- `media-driver` - starts a Java `MediaDriver` as a separate process.

- `c-media-driver` - starts a C `MediaDriver` as a separate process.
A locally built Aeron C driver will run by default. However, it is possible to specify an executable via the
`AERON_MD` environment variable.

For example:
```
AERON_MD=path_to_my_aeron_driver ./c-media-driver
```

Overriding properties
---------------------

There are three ways to define and/or override properties:

1. Create a file named `benchmark.properties` and define your properties there.
    
    The example below illustrates setting property `uk.co.real_logic.benchmarks.remote.messages` to `1000000`:
    
    ```
    uk.co.real_logic.benchmarks.remote.messages=1000000
    ```

1. Supply custom properties file(s) as the last argument to a script, e.g.:
    
    ```
    ./echo-client my-custom-props.properties
    ```

1. Set system properties via `JVM_OPTS` environment variable, e.g.:
    
    ```
    export JVM_OPTS="${JVM_OPTS} -Duk.co.real_logic.benchmarks.aeron.remote.fragmentLimit=25"
    
    ./live-replay-client
    ```
