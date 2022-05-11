Overview
--------

This directory provides scripts to benchmark Aeron using the `uk.co.real_logic.benchmarks.remote.LoadTestRig`.

All benchmarks require two nodes: "client" and "server", where "client" is the
`uk.co.real_logic.benchmarks.remote.LoadTestRig` that uses one of the
`uk.co.real_logic.benchmarks.remote.message.transceiver`implementations under the hood, and the "server" is the
remote node that pipes messages through.

NOTE: It is advised to have "client" and "server" run on different machines.

Test scenarios
--------------

Three different test scenarios are covered:
1. Echo benchmark (aka ping-pong)

    Start the scripts in the following order: `echo-server` -> `echo-client`.

2. Live replay from a remote archive
    
    The client publishes messages to the server using publication over UDP. The server pipes those messages into a
    local IPC publication which records them into an archive. Finally, the client subscribes to the replay from that
    archive over UDP.
    
    Start the scripts in the following order: `live-replay-server` -> `live-replay-client`.

3. Live recording, i.e. client runs records a publication into a local archive
    
    The client publishes messages over UDP to the server. It also has a recording running on that publication using
    local archive. The server simply pipes message back. Finally, the client performs a controlled poll on the
    subscription from the server limited by the "recording progress" which it gets via the recording events.

   The biggest difference between the two is that there is no replay of the recorded messages and hence no reading from
   the disc while guaranteeing consumption of only the persisted messages.
    
    Start the scripts in the following order: `echo-server` -> `live-recording-client`.

4. Echo cluster benchmark

   Similar to the echo benchmark but with the messages being sent to the cluster using
   `io.aeron.cluster.client.AeronCluster.tryClaim` and received from the cluster using
   `io.aeron.cluster.client.AeronCluster.pollEgress` API.

   Start the scripts in the following order: `cluster-node` ... -> `cluster-client`.

   Look in the `scripts/samples/cluster_localhost` for an example of using the cluster benchmark running on localhost.
   A simple approach for setting up a cluster test is to separate the configuration that is common across the cluster and specific to individual nodes/client into separate files.
   The common cluster configuration options that need to be set are:
   For each cluster node the following properties must be configured:
   - `aeron.cluster.members` - the static list of cluster nodes.
   - `aeron.cluster.replication.channel` - Endpoint used for replicating logs between various nodes during elections.
     Can be the node's address (e.g. localhost when testing locally) with a port of 0 (dynamically assigned).
   - `aeron.cluster.ingress.channel` - an ingress channel to which messages to the cluster will be sent. Can be
     configured as multicast for efficiency. Must match the first part of the corresponding cluster members entry.
   - `aeron.cluster.ingress.endpoints` - a list of ingress endpoints which will be substituted into
     `aeron.cluster.ingress.channel` for endpoints. Should not be set if the `aeron.cluster.ingress.channel` is
     multicast. This is client-only property.
   
   For each Cluster node the follow options need to be set.
   When running locally these need to be different to avoid having nodes collide into each other.  
   - `aeron.dir` - a dedicated directory for the `MediaDriver`.
   - `aeron.archive.dir` - a dedicated directory for the `Archive`.
   - `aeron.cluster.dir` - a dedicated directory for the `ConsensusModule` and `ClusteredService`.
   - `aeron.cluster.member.id` - unique ID of the cluster node, must be one of the IDs in the cluster members list.

   For the client the following options need to be set:
   - `aeron.dir` - a dedicated directory for the `MediaDriver`.
   - `aeron.cluster.egress.channel` - the channel for the cluster to send message back to the client.
5. Before running the nodes and client, each will need a media driver running.
   The commands will look similar to the following.
   Each command will block, so will need to be run in separate terminals.
   The default media driver and memory settings are configured for performance, so when running locally you may need to turn down the amount of memory committed up front to prevent OOMEs.

```bash
> JVM_OPTS='-Xms16M' ./scripts/aeron/media-driver ./scripts/samples/cluster_localhost/cluster.properties ./scripts/samples/cluster_localhost/node0.properties
> JVM_OPTS='-Xms16M' ./scripts/aeron/cluster-node ./scripts/samples/cluster_localhost/cluster.properties ./scripts/samples/cluster_localhost/node0.properties

> JVM_OPTS='-Xms16M' ./scripts/aeron/media-driver ./scripts/samples/cluster_localhost/cluster.properties ./scripts/samples/cluster_localhost/node1.properties
> JVM_OPTS='-Xms16M' ./scripts/aeron/cluster-node ./scripts/samples/cluster_localhost/cluster.properties ./scripts/samples/cluster_localhost/node1.properties

> JVM_OPTS='-Xms16M' ./scripts/aeron/media-driver ./scripts/samples/cluster_localhost/cluster.properties ./scripts/samples/cluster_localhost/node2.properties
> JVM_OPTS='-Xms16M' ./scripts/aeron/cluster-node ./scripts/samples/cluster_localhost/cluster.properties ./scripts/samples/cluster_localhost/node2.properties

> JVM_OPTS='-Xms16M' ./scripts/aeron/media-driver ./scripts/samples/cluster_localhost/cluster.properties ./scripts/samples/cluster_localhost/client.properties
> JVM_OPTS='-Xms16M' ./scripts/aeron/cluster-client ./scripts/samples/cluster_localhost/cluster.properties ./scripts/samples/cluster_localhost/client.properties
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
    
    The example below illustrates setting property `uk.co.real_logic.benchmarks.remote.message.rate` to `1000000`:
    
    ```
    uk.co.real_logic.benchmarks.remote.message.rate=1000000
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
