/*
 * Copyright 2015-2023 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <chrono>

#include "benchmark/benchmark.h"

using namespace std;

static void BM_steady_clock(benchmark::State &state)
{
    while (state.KeepRunning())
    {
        benchmark::DoNotOptimize(chrono::steady_clock::now());
    }
}

BENCHMARK(BM_steady_clock);

static void BM_high_resolution_clock(benchmark::State &state)
{
    while (state.KeepRunning())
    {
        benchmark::DoNotOptimize(chrono::high_resolution_clock::now());
    }
}

BENCHMARK(BM_high_resolution_clock);

static void BM_system_clock(benchmark::State &state)
{
    while (state.KeepRunning())
    {
        benchmark::DoNotOptimize(chrono::system_clock::now());
    }
}

BENCHMARK(BM_system_clock);

BENCHMARK_MAIN();
