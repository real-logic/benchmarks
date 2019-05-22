/*
 * Copyright 2014-2019 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
#include <inttypes.h>

#include "Aeron.h"
#include "concurrent/BusySpinIdleStrategy.h"

extern "C"
{
#include "concurrent/aeron_spsc_concurrent_array_queue.h"
#include "util/aeron_error.h"
}

using namespace aeron::concurrent;
using namespace aeron;

#define MAX_THREAD_COUNT (1)
#define FRAGMENT_LIMIT (128)
#define STREAM_ID (11)

int SENTINEL = 0;

typedef std::array<std::uint8_t, sizeof(std::int32_t)> src_buffer_t;

class PublicationState
{
public:
    PublicationState(
        std::size_t burstLength,
        int id,
        std::shared_ptr<ExclusivePublication> publication,
        aeron_spsc_concurrent_array_queue_t *responseQueue) :
        m_srcBuffer(m_src, 0),
        m_burstLength(burstLength),
        m_publication(std::move(publication)),
        m_values(new std::int32_t[burstLength]),
        m_responseQueue(responseQueue)
    {
        m_src.fill(0);

        for (std::size_t i = 0; i < burstLength; i++)
        {
            m_values[i] = -(burstLength - i);
        }

        m_values[burstLength - 1] = id;
    }

    ~PublicationState()
    {
        delete [] m_values;
    }

    void sendBurst()
    {
        for (std::size_t i = 0; i < m_burstLength; i++)
        {
            m_srcBuffer.putInt32(0, m_values[i]);
            while (m_publication->offer(m_srcBuffer) < 0)
            {
                m_idle.idle();
            }
        }

        while (aeron_spsc_concurrent_array_queue_poll(m_responseQueue) == nullptr)
        {
            m_idle.idle();
        }
    }

    void operator()()
    {
        sendBurst();
    }

private:
    AERON_DECL_ALIGNED(src_buffer_t m_src, 16);

    AtomicBuffer m_srcBuffer;
    BusySpinIdleStrategy m_idle;

    const std::size_t m_burstLength;
    std::shared_ptr<ExclusivePublication> m_publication;
    std::int32_t *m_values;
    aeron_spsc_concurrent_array_queue_t *m_responseQueue;
};

class SharedState
{
public:
    SharedState() :
        isSetup(false),
        running(true)
    {
        for (std::size_t i = 0; i < MAX_THREAD_COUNT; i++)
        {
            if (aeron_spsc_concurrent_array_queue_init(&responseQueues[i], 1024) < 0)
            {
                throw std::runtime_error("could not init responseQueue: " + std::string(aeron_errmsg()));
            }
        }
    }

    ~SharedState()
    {
        running = false;
        subscribptionThread.join();

        for (std::size_t i = 0; i < MAX_THREAD_COUNT; i++)
        {
            aeron_spsc_concurrent_array_queue_close(&responseQueues[i]);
        }
    }

    void setup()
    {
        if (!isSetup)
        {
            aeron = Aeron::connect();

            const std::int64_t publicationId = aeron->addExclusivePublication("aeron:ipc", STREAM_ID);
            const std::int64_t subscriptionId = aeron->addSubscription("aeron:ipc", STREAM_ID);

            publication = aeron->findExclusivePublication(publicationId);
            subscription = aeron->findSubscription(subscriptionId);

            while (!subscription)
            {
                std::this_thread::yield();
                subscription = aeron->findSubscription(subscriptionId);
            }

            while (!publication)
            {
                std::this_thread::yield();
                publication = aeron->findExclusivePublication(publicationId);
            }

            while (!publication->isConnected())
            {
                std::this_thread::yield();
            }

            subscribptionThread = std::thread([&]()
            {
                subscriberLoop();
            });;

            isSetup = true;
        }
    }

    void awaitSetup()
    {
        while (!isSetup)
        {
            std::this_thread::yield();
        }
    }

    void operator()(AtomicBuffer& buffer, util::index_t offset, util::index_t length, Header& header)
    {
        const std::int32_t value = buffer.getInt32(offset);
        if (value >= 0)
        {
            while (aeron_spsc_concurrent_array_queue_offer(&responseQueues[value], &SENTINEL) != AERON_OFFER_SUCCESS)
            {
                busySpinIdle.idle();
            }
        }
    }

    void subscriberLoop()
    {
        while (!subscription->isConnected())
        {
            std::this_thread::yield();
        }

        while (true)
        {
            const int fragmentCount = subscription->poll(*this, FRAGMENT_LIMIT);
            if (0 == fragmentCount)
            {
                if (!running)
                {
                    break;
                }

                busySpinIdle.idle();
            }
        }
    }

    aeron_spsc_concurrent_array_queue_t responseQueues[MAX_THREAD_COUNT];
    std::shared_ptr<Aeron> aeron;
    std::shared_ptr<ExclusivePublication> publication;
    std::shared_ptr<Subscription> subscription;
    std::atomic<bool> isSetup;
    std::atomic<bool> running;
    BusySpinIdleStrategy busySpinIdle;
    std::thread subscribptionThread;
};

SharedState sharedState;

static void BM_AeronExclusiveIpcBenchmark(benchmark::State &state)
{
    const std::size_t burstLength = state.range(0);

    sharedState.setup();

    PublicationState publicationState(
        burstLength, 0, sharedState.publication, &sharedState.responseQueues[0]);

    for (auto _ : state)
    {
        publicationState.sendBurst();
    }

    char label[256];

    std::snprintf(label, sizeof(label) - 1, "Burst Length: %" PRIu64, static_cast<std::uint64_t>(burstLength));
    state.SetLabel(label);
}

BENCHMARK(BM_AeronExclusiveIpcBenchmark)
    ->RangeMultiplier(4)
    ->Range(1,100)
    ->UseRealTime();

BENCHMARK_MAIN();
