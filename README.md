# Benchmarks

## Local benchmarks (single machine)
Set of latency benchmarks testing round trip time (RTT) between threads or processes via FIFO data structures and messaging systems.

### Java Benchmarks

To run the Java benchmarks execute the Gradle script in the base directory.

    $ ./gradlew
    
    $ ./gradlew runJavaBenchmarks

or just the Aeron benchmarks

    $ ./gradlew runAeronJavaBenchmarks

### C++ Benchmarks

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

## Remote benchmarks (multiple machines)

The `scripts` directory contains scripts to run the _remote benchmarks_, i.e. the benchmarks that involve multiple
machines where one is the _client_ (the benchmarking harness) and the rest are the _server nodes_.

The `uk.co.real_logic.benchmarks.remote.LoadTestRig` class implements the benchmarking harness. Whereas the
`uk.co.real_logic.benchmarks.remote.Configuration` class provides the configuration for the benchmarking harness.

Before the benchmarks can be executed they have to be built. This can be done by running the following command in the
root directory of this project:
```bash
./gradlew clean deployTar
```
Once complete it will create a `build/distributions/benchmarks.tar` file that should be deployed to the remote machines.

### Running benchmarks via SSH (i.e. automated way)

The easiest way to run the benchmarks is by using the `remote_*` wrapper scripts which invoke scripts remotely using
the SSH protocol. When the script finishes its execution it will download an archive with the results (histograms).

The following steps are required to run the benchmarks:
1. Build the tar file (see above).
2. Copy tar file to the destination machines and unpack it, i.e. `tar xf benchmarks.tar -C <destination_dir>`.
3. On the local machine create a wrapper script that sets all the configuration parameters for the target benchmark.
See example below.
4. Run the wrapper script from step 3.
5. Once execution is finished an archive file with the results will be downloaded. By default, it will be placed under 
the `scripts` directory in this project.

For example here is how to run the Aeron echo benchmarks via a wrapper script.
_NB: All the values in angle brackets (`<...>`) will have to be replaced with proper values._
```bash
# SSH connection properties
export SSH_USER=<SSH user>
export SSH_KEY_FILE=<private SSH key for connecting to client and server machines>
export SSH_CLIENT_NODE=<IP of the client machine>
export SSH_SERVER_NODE=<IP of the server machine>

# Set of required configuration options
export CLIENT_BENCHMARKS_PATH=<directory containing the unpacked benchmarks.tar>
export CLIENT_JAVA_HOME=<path to JAVA_HOME (JDK 8+)>
export CLIENT_DRIVER_CONDUCTOR_CPU_CORE=<CPU core to pin the 'conductor' thread>
export CLIENT_DRIVER_SENDER_CPU_CORE=<CPU core to pin the 'sender' thread>
export CLIENT_DRIVER_RECEIVER_CPU_CORE=<CPU core to pin the 'receiver' thread>
export CLIENT_DRIVER_OTHER_CPU_CORES=<other CPU cores for non-pinned threads of the MediaDriver process>
export CLIENT_LOAD_TEST_RIG_MAIN_CPU_CORE=<CPU core to pin 'load-test-rig' thread>
export CLIENT_LOAD_TEST_RIG_OTHER_CPU_CORES=<other CPU cores for non-pinned threads of the LoadTestRig process>
export SOURCE_IP=<IP address of the client machine>
export CLIENT_INTERFACE=${SOURCE_IP}/24
export CLIENT_AERON_DPDK_GATEWAY_IPV4_ADDRESS=${SOURCE_IP%.*}.1
export SERVER_BENCHMARKS_PATH=<directory containing the unpacked benchmarks.tar>
export SERVER_JAVA_HOME=<path to JAVA_HOME (JDK 8+)>
export SERVER_DRIVER_CONDUCTOR_CPU_CORE=<CPU core to pin the 'conductor' thread>
export SERVER_DRIVER_SENDER_CPU_CORE=<CPU core to pin the 'sender' thread>
export SERVER_DRIVER_RECEIVER_CPU_CORE=<CPU core to pin the 'receiver' thread>
export SERVER_DRIVER_OTHER_CPU_CORES=<other CPU cores for non-pinned threads of the MediaDriver process>
export SERVER_ECHO_CPU_CORE=<CPU core to pin 'echo' thread>
export SERVER_OTHER_CPU_CORES=<other CPU cores for non-pinned threads of the EchoNode process>
export DESTINATION_IP=<IP address of the server machine>
export SERVER_INTERFACE=${DESTINATION_IP}/24
export SERVER_AERON_DPDK_GATEWAY_IPV4_ADDRESS=${DESTINATION_IP%.*}.1

# (Optional) Overrides for the runner configuration options 
export MESSAGE_LENGTH="288" # defaults to "32,288,1344"
export MESSAGE_RATE="100K"  # defaults to "1M,500K,100K"

# Invoke the actual script and optionally configure specific parameters
"aeron/remote-echo-benchmarks" --mtu 8192 --no-onload --no-dpdk --no-ef_vi --no-ats --context "my-test"
```

### Running benchmarks manually

The following steps are required to run the benchmarks:
1. Build the tar file (see above).
2. Copy tar file to the destination machines and unpack it, i.e. `tar xf benchmarks.tar -C <destination_dir>`.
3. Follow the documentation for a particular benchmark to know which scripts to run and in which order.
4. Run the `benchmark-runner` script specifying the _benchmark client script_ to execute.

Here is an example of running the Aeron echo benchmark using the embedded Java MediaDriver on two nodes:
server (`192.168.0.20`) and client (`192.168.0.10`).
```bash
server:~/benchmarks/scripts$ JVM_OPTS="\
-Duk.co.real_logic.benchmarks.aeron.remote.embedded.media.driver=true \
-Duk.co.real_logic.benchmarks.aeron.remote.source.channel=aeron:udp?endpoint=192.168.0.10:13000 \
-Duk.co.real_logic.benchmarks.aeron.remote.destination.channel=aeron:udp?endpoint=192.168.0.20:13001" aeron/echo-server

client:~/benchmarks/scripts$ JVM_OPTS="\
-Duk.co.real_logic.benchmarks.aeron.remote.embedded.media.driver=true \
-Duk.co.real_logic.benchmarks.aeron.remote.source.channel=aeron:udp?endpoint=192.168.0.10:13000 \
-Duk.co.real_logic.benchmarks.aeron.remote.destination.channel=aeron:udp?endpoint=192.168.0.20:13001" \
./benchmark-runner --output-file "aeron-echo-test" --messages "100K" --burst-size "1" --message-length "288" --iterations 30 --runs 1 "aeron/echo-client"
```
_**Note**: At the end of a single run the server-side process (e.g. `aeron/echo-server`) will exit, i.e. in order to do
another manual run (with different parameters etc.) one has to start the server process again. Alternative is to run the
benchmarks [over the SSH](#running-benchmarks-via-ssh-ie-automated-way)._

### Aggregating the results

To aggregate the results of the multiple runs into a single file use the `aggregate-results` script.

For example if the ``results`` directory contains the following files:
```bash
results
├── echo-test_rate=1000_batch=1_length=32_sha=c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-0.hdr
├── echo-test_rate=1000_batch=1_length=32_sha=c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-1.hdr
├── echo-test_rate=1000_batch=1_length=32_sha=c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-2.hdr
├── echo-test_rate=1000_batch=1_length=32_sha=c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-3.hdr
└── echo-test_rate=1000_batch=1_length=32_sha=c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-4.hdr
```   

Running:
```bash
./aggregate-results results
```

Will produce the following result:
```bash
results
├── echo-test_rate=1000_batch=1_length=32_sha=c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-0.hdr
├── echo-test_rate=1000_batch=1_length=32_sha=c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-1.hdr
├── echo-test_rate=1000_batch=1_length=32_sha=c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-2.hdr
├── echo-test_rate=1000_batch=1_length=32_sha=c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-3.hdr
├── echo-test_rate=1000_batch=1_length=32_sha=c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-4.hdr
├── echo-test_rate=1000_batch=1_length=32_sha=c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-combined.hdr
└── echo-test_rate=1000_batch=1_length=32_sha=c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-report.hgrm
```
where `echo-test_rate=1000_batch=1_length=32_sha=c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-combined.hdr` is the
aggregated histogram of five runs and the 
`echo-test_rate=1000_batch=1_length=32_c7a083c84b45f77fdee5cedc272d898d44b6e18deaf963b3e2b2c074006b0b10-report.hgrm` is an export of the
aggregated histogram that can be plotted using http://hdrhistogram.github.io/HdrHistogram/plotFiles.html.

### Plotting the results

Aggregated results can be plotted using the `results-plotter.py` script which uses [hdr-plot](https://github.com/BrunoBonacci/hdr-plot) in order to produce latency plots of the histograms (the library needs to be installed in order to use the script).

Running

```bash
./results-plotter.py results
```

will produce plots in which the histograms are grouped by test scenario by default. It is possible to produce graphs with a different kind of aggregation and to apply filters on the histograms to plot within a directory. Run `./results-plotter.py` (without arguments) in order to get an overview of the capabilities of the plotting script.

### Systems under test

This section lists the systems under test which implement remote benchmarks.

#### Aeron

Aeron remote benchmarks implement the following test scenarios:
1. Echo benchmark (aka ping-pong).

2. Live replay from remote archive.

The client publishes messages to the server using publication over UDP. The server pipes those messages into a local IPC
publication which records them into an archive. Finally, the client subscribes to the replay from that archive over UDP.

3. Live recording, i.e. client runs records a publication into local archive.

The client publishes messages over UDP to the server. It also has a recording running on that publication using local
archive. The server simply pipes message back. Finally, the client performs a controlled poll on the subscription from
the server limited by the "recording progress" which it gets via the recording events.

The biggest difference between scenario 2 and this scenario is that there is no replay of recorded message and hence no
reading from the disc of the saved data but still allowing consumption of those messages that were successfully
persisted.

4. Cluster echo benchmark.

The client sends messages to the Aeron Cluster over UDP. The Cluster sequences the message into a single log, reaches
the consensus on the received messages, processes them and then replies to the client over UDP.

Please the documentation in the ``aeron`` directory for more information.

#### gRPC

For the gRPC there is only ping-pong test with a single implementation:
- Streaming client - client uses streaming API to send/receive messages.

Please read the documentation in then ``grpc`` directory for more information.

#### Kafka

Unlike the gRPC that simply echoes messages Kafka will persist them so the test is similar to the Aeron's replay from
the remote archive.

Please read the documentation in the `kafka` directory for more information.

License (See LICENSE file for full license)
-------------------------------------------
Copyright 2015-2023 Real Logic Limited.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
