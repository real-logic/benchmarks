/*
 * Copyright 2015-2020 Real Logic Limited.
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

#define DISABLE_BOUNDS_CHECKS 1

#include <string>
#include <benchmark/benchmark.h>
#include <thread>
#include <atomic>

#include "concurrent/Atomic64.h"
#include "concurrent/BusySpinIdleStrategy.h"

extern "C"
{
#include "concurrent/aeron_spsc_concurrent_array_queue.h"
#include "util/aeron_error.h"
}

using namespace aeron::concurrent;

int SENTINEL = 1000;

static void BM_C_SpscQueueLatency(benchmark::State &state)
{
    const auto burstLength = static_cast<std::size_t>(state.range(0));
    aeron_spsc_concurrent_array_queue_t q_in;
    aeron_spsc_concurrent_array_queue_t q_out;

    if (aeron_spsc_concurrent_array_queue_init(&q_in, 64 * 1024) < 0)
    {
        throw std::runtime_error("could not init q_in: " + std::string(aeron_errmsg()));
    }

    if (aeron_spsc_concurrent_array_queue_init(&q_out, 128) < 0)
    {
        throw std::runtime_error("could not init q_out: " + std::string(aeron_errmsg()));
    }

    auto *values = new std::int32_t[burstLength];
    auto burstLengthValue = static_cast<std::int32_t>(burstLength);
    for (std::int32_t i = 0; i < burstLengthValue; i++)
    {
        values[static_cast<std::size_t>(i)] = -(burstLengthValue - i);
    }

    values[burstLength - 1] = 0;

    std::atomic<bool> start{ false };
    std::atomic<bool> running{ true };

    std::thread t(
        [&]()
        {
            BusySpinIdleStrategy idle;
            start.store(true);

            while (running)
            {
                int *p = (int *)aeron_spsc_concurrent_array_queue_poll(&q_in);
                if (p != nullptr)
                {
                    if (*p >= 0)
                    {
                        while (aeron_spsc_concurrent_array_queue_offer(&q_out, &SENTINEL) != AERON_OFFER_SUCCESS)
                        {
                            idle.idle();
                        }
                    }
                }
                else
                {
                    idle.idle();
                }
            }
        });

    while (!start)
    {
        std::this_thread::yield();
    }

    BusySpinIdleStrategy burstIdle;

    while (state.KeepRunning())
    {
        for (std::size_t i = 0; i < burstLength; i++)
        {
            while (aeron_spsc_concurrent_array_queue_offer(&q_in, &values[i]) != AERON_OFFER_SUCCESS)
            {
                burstIdle.idle();
            }
        }

        while (aeron_spsc_concurrent_array_queue_poll(&q_out) == nullptr)
        {
            burstIdle.idle();
        }
    }

    state.SetItemsProcessed(state.iterations());
    state.SetBytesProcessed(state.iterations() * sizeof(int));

    running.store(false);

    t.join();

    delete [] values;

    aeron_spsc_concurrent_array_queue_close(&q_in);
    aeron_spsc_concurrent_array_queue_close(&q_out);
}

BENCHMARK(BM_C_SpscQueueLatency)
    ->Arg(1)
    ->Arg(100)
    ->UseRealTime();

static void BM_C_SpscQueueThroughput(benchmark::State &state)
{
    int *i = new int{ 42 };
    aeron_spsc_concurrent_array_queue_t q;

    if (aeron_spsc_concurrent_array_queue_init(&q, static_cast<std::size_t>(state.range(0))) < 0)
    {
        throw std::runtime_error("could not init q: " + std::string(aeron_errmsg()));
    }

    std::atomic<bool> start{ false };
    std::atomic<bool> running{ true };
    std::int64_t totalMsgs = 0;

    std::thread t(
        [&]()
        {
            start.store(true);

            while (running)
            {
                if (nullptr == aeron_spsc_concurrent_array_queue_poll(&q))
                {
                    aeron::concurrent::atomic::cpu_pause();
                }
            }
        });

    while (!start)
    {
        std::this_thread::yield();
    }

    while (state.KeepRunning())
    {
        while (aeron_spsc_concurrent_array_queue_offer(&q, i) != AERON_OFFER_SUCCESS)
        {
            aeron::concurrent::atomic::cpu_pause();
        }

        totalMsgs++;
    }

    state.SetItemsProcessed(totalMsgs);
    state.SetBytesProcessed(totalMsgs * sizeof(int));

    running.store(false);

    t.join();
}

BENCHMARK(BM_C_SpscQueueThroughput)
    ->Arg(1024)
    ->Arg(64 * 1024)
    ->Arg(128 * 1024)
    ->Arg(256 * 1024)
    ->UseRealTime();

BENCHMARK_MAIN();
