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

#define MAX_THREAD_COUNT (4)
#define FRAGMENT_LIMIT (128)
#define STREAM_ID (10)

int SENTINEL = 0;

typedef std::array<std::uint8_t, sizeof(std::int32_t)> src_buffer_t;

class PublicationState
{
public:
    PublicationState(
        std::size_t burstLength,
        int id,
        std::shared_ptr<Publication> publication,
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
    std::shared_ptr<Publication> m_publication;
    std::int32_t *m_values;
    aeron_spsc_concurrent_array_queue_t *m_responseQueue;
};

class SubscriberState
{
public:
    SubscriberState(std::shared_ptr<Subscription> subscription, aeron_spsc_concurrent_array_queue_t *responseQueues) :
        m_subscription(std::move(subscription)),
        m_running(true),
        m_responseQueues(responseQueues)
    {
    }

    void operator()(AtomicBuffer& buffer, util::index_t offset, util::index_t length, Header& header)
    {
        const std::int32_t value = buffer.getInt32(offset);
        if (value >= 0)
        {
            while (aeron_spsc_concurrent_array_queue_offer(&m_responseQueues[value], &SENTINEL) != AERON_OFFER_SUCCESS)
            {
                m_idle.idle();
            }
        }
    }

    void stop()
    {
        m_running = false;
    }

    void operator()()
    {
        while (!m_subscription->isConnected())
        {
            std::this_thread::yield();
        }

        while (true)
        {
            const int fragmentCount = m_subscription->poll(*this, FRAGMENT_LIMIT);
            if (0 == fragmentCount)
            {
                if (!m_running)
                {
                    break;
                }

                m_idle.idle();
            }
        }
    }

private:
    std::shared_ptr<Subscription> m_subscription;
    BusySpinIdleStrategy m_idle;
    std::atomic<bool> m_running;
    aeron_spsc_concurrent_array_queue_t *m_responseQueues;
};

static void BM_AeronIpcBenchmark(benchmark::State &state)
{
    const std::size_t burstLength = state.range(0);
    std::shared_ptr<Aeron> aeron = Aeron::connect();

    const std::int64_t publicationId = aeron->addPublication("aeron:ipc", STREAM_ID);
    const std::int64_t subscriptionId = aeron->addSubscription("aeron:ipc", STREAM_ID);

    std::shared_ptr<Publication> publication = aeron->findPublication(publicationId);
    std::shared_ptr<Subscription> subscription = aeron->findSubscription(subscriptionId);

    while (!subscription)
    {
        std::this_thread::yield();
        subscription = aeron->findSubscription(subscriptionId);
    }

    while (!publication)
    {
        std::this_thread::yield();
        publication = aeron->findPublication(publicationId);
    }

    while (!publication->isConnected())
    {
        std::this_thread::yield();
    }

    aeron_spsc_concurrent_array_queue_t responseQueues[MAX_THREAD_COUNT];

    if (aeron_spsc_concurrent_array_queue_init(&responseQueues[0], 1024) < 0)
    {
        throw std::runtime_error("could not init responseQueue: " + std::string(aeron_errmsg()));
    }

    SubscriberState subscriberThread(subscription, responseQueues);
    PublicationState publicationThread(burstLength, state.thread_index, publication, &responseQueues[state.thread_index]);

    std::thread t([&]()
    {
        subscriberThread();
    });

    for (auto _ : state)
    {
        publicationThread.sendBurst();
    }

    subscriberThread.stop();
    t.join();

    char label[256];

    std::snprintf(label, sizeof(label) - 1, "Threads: %" PRIu32", Burst Length: %" PRIu64,
        static_cast<std::uint32_t>(state.threads),
        static_cast<std::uint64_t>(burstLength));
    state.SetLabel(label);
}

BENCHMARK(BM_AeronIpcBenchmark)
    ->RangeMultiplier(10)
    ->Range(1, 100)
    ->UseRealTime();

BENCHMARK_MAIN();
