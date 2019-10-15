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

__NOTE__: On MacOS, it will be necessary to set `DYLD_LIBRARY_PATH` for the Aeron
driver shared library. For example:

    $ env DYLD_LIBRARY_PATH=cppbuild/Release/aeron-prefix/src/aeron-build/lib cppbuild/Release/binaries/aeronIpcBenchmark

The binaries with __Benchmark__ in the name use Google Benchmark and only displays average times.

While the binaries with __Nanomark__ in the name use Nanomark (included in the source) and displays full histograms.

To pick a specific tag for Aeron, add __GIT_TAG__ to the ExternalProject_Add section in CMakeLists.txt to have
the specific tag in Aeron used. For example, GIT_TAG "1.22.0" will use Aeron 1.22.0 release.

License (See LICENSE file for full license)
-------------------------------------------
Copyright 2015-2019 Real Logic Limited

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
