Overview
--------

This directory provides scripts to benchmark Aeron using the `uk.co.real_logic.benchmarks.remote.LoadTestRig`.

All benchmarks require two nodes: "client" and "server", where "client" is the
`uk.co.real_logic.benchmarks.remote.LoadTestRig` that uses one of the `uk.co.real_logic.benchmarks.remote.MessageTransceiver`
implementations under the hood, and the "server" is the remote node that pipes messages through.
NOTE: It is advised to have "client" and "server" run on different machines.


Test scenarios
--------------

Four different test scenarios are covered:
1. Echo benchmark (aka ping-pong)

Start the scripts in the following order: `echo-server` --> `echo-client`.

2. Live replay from remote archive

The client publishes messages to the server using publication over UDP. The server pipes those messages into a local IPC
publication which records them into an archive. Finally, the client subscribes to the replay from that archive over UDP.

Start the scripts in the following order: `live-replay-remote-archive-server` --> `live-replay-remote-archive-client`.

3. Live recording, i.e. client runs records a publication into local archive

The client publishes messages over UDP to the server. It also has a recording running on that publication using local
archive. The server simply pipes message back. Finally, the client performs a controlled poll on the subscription from
the server limited by the "recording progress" which it gets via the recording events.

The biggest difference between scenario 2 and this scenario is that there is no replay of recorded message and hence no
reading from the disc of the saved data but still allowing consumption of those messages that were successfully
persisted.

Start the scripts in the following order: `echo-server` --> `live-recording-client`.


Helper scripts
--------------

Besides the scripts to run the client and server for each case, there are several helper scripts:
- `media-driver` - starts a Java `MediaDriver` as a separate process.

- `c-media-driver` - starts a C `MediaDriver` as a separate process.
By default it will run locally built Aeron C driver. However, it is possible to specify an executable via the
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

2. Supply custom properties file(s) as the last argument to a script, e.g.:

```
./echo-client my-custom-props.properties
```

3. Set system properties via `JVM_OPTS` environment variable, e.g.:

```
export JVM_OPTS="${JVM_OPTS} -Duk.co.real_logic.benchmarks.aeron.remote.fragmentLimit=25"

./run-with-embedded-media-driver ./live-replay-remote-archive-client
```
