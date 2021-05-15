Overview
--------

This directory provides scripts to benchmark Kafka using the `uk.co.real_logic.benchmarks.remote.LoadTestRig`.

All benchmarks require two nodes: "client" and "server", where "client" is the
`uk.co.real_logic.benchmarks.remote.LoadTestRig` that uses one of the
`uk.co.real_logic.benchmarks.remote.message.transceiver` implementations under the hood, and the "server" is the
remote node that pipes messages through.

NOTE: It is advised to have "client" and "server" run on different machines.


Test scenarios
--------------

Echo benchmarks for Kafka consists of sending a message to the server (broker) and reading the same message back. Since
Kafka persists messages one can change configuration to obtain different test scenarios.


Running the benchmarks
----------------------
1. Start Zookeeper
    ```bash
    ./zookeeper-start
    ```

1. Start Kafka
    ```bash
    ./kafka-start
    ```

1. Start benchmark
    ```bash
    ./client
    ```

To stop Zookeeper and Kafka use the `stop-all` script.

Configuration
-------------
* `uk.co.real_logic.benchmarks.kafka.remote.partition` - partition selection for the client (i.e. 
`uk.co.real_logic.benchmarks.kafka.remote.PartitionSelection`). One of the following:
  * `EXPLICIT` - an explicit partition will be assigned. Default if no other strategy is specified.
  * `BY_KEY` - a partition should be selected using a key from the record.
  * `RANDOM` - a partition should be selected at random by the broker when message arrives.
* `uk.co.real_logic.benchmarks.kafka.remote.producer.max.in.flight.messages` - max number of the in-flight messages
that can be sent by the producer. Default value is `1000`. When this limit is reached the producer will stop sending
new messages until the callback is invoked.
* `bootstrap.servers` - Kafka server location, i.e. host/port pair, e.g. `localhost:13592`.
* `security.protocol` - security protocol to use. Supported values:
  * `PLAINTEXT` - message will be sent over HTTP.
  * `SSL` - message will be sent over HTTPS.
* `acks` - number of messages after which the server must send the acknowledged response.
* `flush.messages` - after how many messages server should flush data to disc. If the property is not specified then
no explicit flush will be done.

Besides the properties listed above there are several config files:
* `server.properties` - Kafka server (broker) config.
* `client.properties` - client-side config (i.e. consumer/producer configs).
* `topic.properties` - topic configuration.
* `zookeeper.properties` - Zookeeper config.
* `log4j.properties` - Log4j config for Kafka.


Overriding properties
---------------------

There are three ways to define and/or override properties:

1. Create a file named `benchmark.properties` and define your properties there.

    For example:
    ```
    bootstrap.servers=localhost:13593
    security.protocol=SSL
    acks=1
    flush.messages=1
    ```

1. Supply custom properties file(s) as the last argument to a script, e.g.:

    ```
    ./client my-custom-props.properties
    ```

1. Set system properties via `JVM_OPTS` environment variable, e.g.:

    ```
    export JVM_OPTS="${JVM_OPTS} -Duk.co.real_logic.benchmarks.kafka.remote.producer.max.in.flight.messages=500"
    
    ./client
    ```
