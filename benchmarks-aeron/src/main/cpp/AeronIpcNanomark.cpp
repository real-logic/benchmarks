/*
 * Copyright 2015-2025 Real Logic Limited.
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
#define EMBEDDED_MEDIA_DRIVER

#include <iostream>
#include <string>
#include <thread>
#include <atomic>
#include <array>

#include "NanoMark.h"

#include "Aeron.h"
#include "concurrent/BusySpinIdleStrategy.h"
#include "EmbeddedAeronMediaDriver.h"

extern "C"
{
#include "concurrent/aeron_spsc_concurrent_array_queue.h"
#include "util/aeron_error.h"
}

using namespace aeron::concurrent;
using namespace aeron;

#define MAX_THREAD_COUNT (4)
#define FRAGMENT_LIMIT (128)
#define STREAM_ID (11)
#define MAX_BURST_LENGTH (100)
#define RESPONSE_QUEUE_CAPACITY (128)
#define USE_TRY_CLAIM

int SENTINEL = 0;

typedef std::array<std::uint8_t, (sizeof(std::int32_t) * MAX_BURST_LENGTH)> src_buffer_t;

template<typename P, typename IdleStrategy = BusySpinIdleStrategy>
class Burster
{
public:
    Burster(
        std::size_t burstLength,
        int id,
        std::shared_ptr<P> publication,
        aeron_spsc_concurrent_array_queue_t *responseQueue) :
        m_srcBuffer(m_src, 0),
        m_savedPublication(std::move(publication)),
        m_responseQueue(responseQueue),
        m_publication(m_savedPublication.get()),
        m_burstLength(burstLength)
    {
        m_src.fill(0);

        auto burstLengthValue = static_cast<std::int32_t>(burstLength);
        for (std::int32_t i = 0; i < burstLengthValue; i++)
        {
            m_values[static_cast<std::size_t>(i)] = -(burstLengthValue - i);
        }

        m_values[burstLength - 1] = id;
    }

    inline void sendBurst()
    {
        auto iter = m_values.begin();

        for (std::size_t i = 0; i < m_burstLength; ++iter, ++i)
        {
#ifdef USE_TRY_CLAIM
            while (m_publication->tryClaim(sizeof(std::int32_t), m_bufferClaim) < 0)
            {
                IdleStrategy::pause();
            }

            m_bufferClaim.buffer().putInt32(m_bufferClaim.offset(), *iter);
            m_bufferClaim.commit();
#else
            m_srcBuffer.putInt32(0, *iter);
            while (m_publication->offer(m_srcBuffer, 0, sizeof(std::int32_t), DEFAULT_RESERVED_VALUE_SUPPLIER) < 0)
            {
                IdleStrategy::pause();
            }
#endif
        }
    }

    inline void awaitConfirm()
    {
        while (aeron_spsc_concurrent_array_queue_poll(m_responseQueue) == nullptr)
        {
            IdleStrategy::pause();
        }
    }

private:
    AERON_DECL_ALIGNED(src_buffer_t m_src, 16) = {};
    AtomicBuffer m_srcBuffer;
    BufferClaim m_bufferClaim;

    std::shared_ptr<P> m_savedPublication;
    std::array<std::int32_t, MAX_BURST_LENGTH> m_values = {};

    aeron_spsc_concurrent_array_queue_t *m_responseQueue = nullptr;
    P *m_publication = nullptr;
    const std::size_t m_burstLength;
};

class SharedState
{
public:
    SharedState() = default;

    ~SharedState()
    {
        running = false;
        subscriptionThread.join();

        for (std::size_t i = 0; i < MAX_THREAD_COUNT; i++)
        {
            aeron_spsc_concurrent_array_queue_close(&responseQueues[i]);
        }

#ifdef EMBEDDED_MEDIA_DRIVER
        driver.stop();
#endif
    }

    void setup()
    {
        if (!isSetup)
        {
            for (std::size_t i = 0; i < MAX_THREAD_COUNT; i++)
            {
                if (aeron_spsc_concurrent_array_queue_init(&responseQueues[i], RESPONSE_QUEUE_CAPACITY) < 0)
                {
                    std::cerr << "could not init responseQueue: " << aeron_errmsg() << std::endl;
                    std::exit(EXIT_FAILURE);
                }
            }

#ifdef EMBEDDED_MEDIA_DRIVER
            driver.start();
#endif

            Context context;
            context.preTouchMappedMemory(true);

            aeron = Aeron::connect(context);

            const std::int64_t publicationId = aeron->addPublication("aeron:ipc", STREAM_ID);
            const std::int64_t subscriptionId = aeron->addSubscription("aeron:ipc", STREAM_ID);

            publication = aeron->findPublication(publicationId);
            subscription = aeron->findSubscription(subscriptionId);

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

            subscriptionThread = std::thread(
                [&]()
                {
                    subscriberLoop();
                });

            isSetup = true;
        }
    }

    void awaitSetup() const
    {
        while (!isSetup)
        {
            std::this_thread::yield();
        }
    }

    void subscriberLoop()
    {
        while (!subscription->isConnected())
        {
            std::this_thread::yield();
        }

        std::shared_ptr<Image> imageSharedPtr = subscription->imageByIndex(0);
        Image &image = *imageSharedPtr;
        auto handler =
            [&](AtomicBuffer &buffer, util::index_t offset, util::index_t, Header &)
            {
                const std::int32_t value = buffer.getInt32(offset);
                if (value >= 0)
                {
                    while (AERON_OFFER_SUCCESS != aeron_spsc_concurrent_array_queue_offer(
                        &responseQueues[value], &SENTINEL))
                    {
                        BusySpinIdleStrategy::pause();
                    }
                }
            };

        while (running)
        {
            if (image.poll(handler, FRAGMENT_LIMIT) == 0)
            {
                BusySpinIdleStrategy::pause();
            }
        }
    }

    AERON_DECL_ALIGNED(aeron_spsc_concurrent_array_queue_t responseQueues[MAX_THREAD_COUNT], 16) = {};
    std::shared_ptr<Aeron> aeron;
    std::shared_ptr<Publication> publication;
    std::shared_ptr<Subscription> subscription;
    std::atomic<bool> isSetup = { false };
    std::atomic<bool> running = { true };
    std::thread subscriptionThread;
#ifdef EMBEDDED_MEDIA_DRIVER
    EmbeddedMediaDriver driver;
#endif
};

static std::size_t benchmarkBurstLength = 1;

class AeronIpcNanomark : public nanomark::Nanomark
{
public:
    void setUp() override
    {
        Nanomark::setUp();

        sharedState.setup();

        for (std::size_t i = 0; i < MAX_THREAD_COUNT; i++)
        {
            bursters[i].reset(new Burster<Publication>(
                benchmarkBurstLength, static_cast<int>(i), sharedState.publication, &sharedState.responseQueues[i]));
        }
    }

    void tearDown() override
    {
        Nanomark::tearDown();
        std::cout << "Summary: " << histogramSummary(histogram()) << std::endl;

        std::cout << "Histogram:" << std::endl;
        printFullHistogram();

        for (std::size_t i = 0; i < MAX_THREAD_COUNT; i++)
        {
            bursters[i].reset(nullptr);
        }
    }

    void perThreadSetUp(std::size_t id, std::size_t repetition) override
    {
        Nanomark::perThreadSetUp(id, repetition);
    }

    void perThreadTearDown(std::size_t id, std::size_t repetition) override
    {
        Nanomark::perThreadTearDown(id, repetition);

        std::ostringstream stream;

        stream << "Thread " << std::to_string(id) << " TearDown " <<
            std::to_string(repetition + 1) << "/" << numberOfMaxRepetitions() <<
            ": " << histogramSummary(histogram(id)) << std::endl;
        std::cout << stream.str();
    }

    void recordRepetition(std::size_t id, std::size_t repetition, std::uint64_t totalNs, std::size_t numberOfRuns)
        override
    {
        std::ostringstream stream;

        Nanomark::recordRepetition(id, repetition, totalNs, numberOfRuns);
        stream << "Thread " << std::to_string(id) << " repetition " << std::to_string(repetition + 1) << ": " <<
            "nanos/op " << std::to_string((double)totalNs / (double)numberOfRuns);
        stream << std::endl;

        std::cout << stream.str();
    }

    static SharedState sharedState;

    std::unique_ptr<Burster<Publication>> bursters[MAX_THREAD_COUNT];
};

SharedState AeronIpcNanomark::sharedState;

NANOMARK(AeronIpcNanomark, burst)(std::size_t id)
{
    Burster<Publication> *burster = bursters[id].get();

    burster->sendBurst();
    burster->awaitConfirm();
}

int main(int argc, char **argv)
{
    for (std::size_t threads = 1; threads < MAX_THREAD_COUNT; threads++)
    {
        benchmarkBurstLength = 1;
        std::cout << "Burst Length = " << std::to_string(benchmarkBurstLength) <<
            " Threads = " << std::to_string(threads) << std::endl;
        ::nanomark::NanomarkRunner::run(threads, 5);
        std::cout << std::endl;

        benchmarkBurstLength = 100;
        std::cout << "Burst Length = " << std::to_string(benchmarkBurstLength) <<
            " Threads = " << std::to_string(threads) << std::endl;
        ::nanomark::NanomarkRunner::run(threads, 5);
    }

    return 0;
}
