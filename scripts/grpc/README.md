Overview
--------

This directory provides scripts to benchmark gRPC using the `uk.co.real_logic.benchmarks.remote.LoadTestRig`.

All benchmarks require two nodes: "client" and "server", where "client" is the
`uk.co.real_logic.benchmarks.remote.LoadTestRig` that uses one of the `uk.co.real_logic.benchmarks.remote.MessageTransceiver`
implementations under the hood, and the "server" is the remote node that pipes messages through.
NOTE: It is advised to have "client" and "server" run on different machines.


Test scenarios
--------------

1. Echo benchmark using blocking client.

    Start the scripts in the following order: `server` --> `blocking-client`.

1. Echo benchmark using streaming client.

    Start the scripts in the following order: `server` --> `streaming-client`.


Configuration
-------------
* `uk.co.real_logic.benchmarks.grpc.remote.server.host` - server host. Default value is `localhost`.
* `uk.co.real_logic.benchmarks.grpc.remote.server.port` - server port number. Default value is `13400`.
* `uk.co.real_logic.benchmarks.grpc.remote.tls` - should the TLS be used for client/server communication.
Default value is `false`.


Overriding properties
---------------------

There are three ways to define and/or override properties:

1. Create a file named `benchmark.properties` and define your properties there.

    For example:
    ```
    uk.co.real_logic.benchmarks.grpc.remote.server.host=127.0.0.1
    uk.co.real_logic.benchmarks.grpc.remote.server.port=13400
    uk.co.real_logic.benchmarks.grpc.remote.tls=true
    ```

1. Supply custom properties file(s) as the last argument to a script, e.g.:

    ```
    ./blocking-client my-custom-props.properties
    ```

1. Set system properties via `JVM_OPTS` environment variable, e.g.:

    ```
    export JVM_OPTS="${JVM_OPTS} -Duk.co.real_logic.benchmarks.grpc.remote.server.host=192.168.10.10"
    
    ./server
    ```
