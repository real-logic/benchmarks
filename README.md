# Benchmarks

Set of latency benchmarks testing round trip time (RTT) between threads or processes via FIFO data structures.

## Java Benchmarks

To run the Java benchmarks execute the Gradle script in the base directory.

    $ ./gradlew
    
    $ ./gradlew runJavaBenchmarks

or just the Aeron benchmarks

    $ ./gradlew runAeronJavaBenchmarks

## C++ Benchmarks

To generate the benchmarks, execute the `cppbuild` script from the base directory.

    $ cppbuild/cppbuild

To run the benchmarks, execute the individual benchmarks.

    $ cppbuild/Release/binaries/baseline
    $ cppbuild/Release/binaries/aeronExclusiveIpcBenchmark
    $ cppbuild/Release/binaries/aeronIpcBenchmark
    $ cppbuild/Release/binaries/aeronExclusiveIpcNanomark
    $ cppbuild/Release/binaries/aeronIpcNanomark

**Note**: On MacOS, it will be necessary to set `DYLD_LIBRARY_PATH` for the Aeron
driver shared library. For example:

    $ env DYLD_LIBRARY_PATH=cppbuild/Release/aeron-prefix/src/aeron-build/lib cppbuild/Release/binaries/aeronIpcBenchmark

The binaries with __Benchmark__ in the name use Google Benchmark and only displays average times.

While the binaries with __Nanomark__ in the name use Nanomark (included in the source) and displays full histograms.

To pick a specific tag for Aeron, specify `--aeron-git-tag` parameter when invoking `cppbuild` script.
For example:
```bash
cppbuild/cppbuild --aeron-git-tag="1.27.0"
```
will use Aeron `1.27.0` release.

## Remote benchmarks

The `scripts` directory contains scripts to run the _remote benchmarks_, i.e. the benchmarks that test sending data
between the _client_ (benchmarking harness) and the _server_ in different scenarios.

`uk.co.real_logic.benchmarks.remote.LoadTestRig` implements the benchmarking harness. The
`uk.co.real_logic.benchmarks.remote.Configuration` class provides the configuration for the benchmarking harness.

#### Running benchmarks

The common way to run the benchmarks is to use `benchmark-runner` to run the benchmarking harness with the given client.
This script will run benchmarks multiple times for each permutation of the configuration parameters.

For example:
```bash
$ ./benchmark-runner --output-file "echo-test" --messages "1000, 5000" --burst-size "1, 10" --message-length "32, 224, 1376" "aeron/echo-client"
```
Will execute `aeron/echo-client` script for every permutation of the number of messages, burst size and the message 
length. Each execution will run five times doing ten measurement iterations. 
The first execution will be with `1000` messages, burst size of `1` and message payload length of `32` bytes. The
second will have a different message length (i.e. `224` bytes) etc. 

#### Aggregating the results

To aggregate the results of the multiple runs into a single file there is the `aggregate-results` script.

For example if the `results` directory contains the following files:
```bash
results
├── echo-test_1000_1_32_c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-0.hdr
├── echo-test_1000_1_32_c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-1.hdr
├── echo-test_1000_1_32_c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-2.hdr
├── echo-test_1000_1_32_c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-3.hdr
└── echo-test_1000_1_32_c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-4.hdr
```   

Running:
```bash
./aggregate-results results
```

Will produce the following result:
```bash
results
├── echo-test_1000_1_32_c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-0.hdr
├── echo-test_1000_1_32_c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-1.hdr
├── echo-test_1000_1_32_c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-2.hdr
├── echo-test_1000_1_32_c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-3.hdr
├── echo-test_1000_1_32_c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-4.hdr
├── echo-test_1000_1_32_c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-combined.hdr
└── echo-test_1000_1_32_c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-report.hgrm
```
where `echo-test_1000_1_32_c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-combined.hdr` is the
aggregated histogram of five runs and the 
`echo-test_1000_1_32_c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-report.hgrm` is an export of the
aggregated histogram that can be plotted using http://hdrhistogram.github.io/HdrHistogram/plotFiles.html.

### Systems under test

This section lists the systems under test which implement remote benchmarks.

#### Aeron

Aeron remote benchmarks implement the following test scenarios:
1. Echo benchmark (aka ping-pong)

2. Live replay from remote archive

The client publishes messages to the server using publication over UDP. The server pipes those messages into a local IPC
publication which records them into an archive. Finally, the client subscribes to the replay from that archive over UDP.

3. Live recording, i.e. client runs records a publication into local archive

The client publishes messages over UDP to the server. It also has a recording running on that publication using local
archive. The server simply pipes message back. Finally, the client performs a controlled poll on the subscription from
the server limited by the "recording progress" which it gets via the recording events.

The biggest difference between scenario 2 and this scenario is that there is no replay of recorded message and hence no
reading from the disc of the saved data but still allowing consumption of those messages that were successfully
persisted.

Please the documentation in the `aeron` directory for more information.

#### gRPC

For gRPC there is only ping-pong test with two different implementations:
- Blocking client - client uses blocking API to send messages.
- Streaming client - client uses streaming API to send/receive messages.

Please the documentation in the `grpc` directory for more information.

#### Kafka

Unlike gRPC that simply echoes messages Kafka will persist them so that the test is similar to the Aeron's replay from
the remote archive.

Please the documentation in the `kafka` directory for more information.

License (See LICENSE file for full license)
-------------------------------------------
Copyright 2015-2021 Real Logic Limited.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
