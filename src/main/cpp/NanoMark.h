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

#ifndef NANOMARK_H
#define NANOMARK_H

#include <iostream>
#include <vector>
#include <thread>

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>

#if defined(__linux__) || defined(Darwin)
#   include <time.h>
#elif defined(WIN32)
#   include <windows.h>
#else
#   error "Must define Darwin or __linux__ or WIN32"
#endif

#define DEFAULT_REPETITIONS (1)
#define DEFAULT_RUN_LENGTH_NS (1000 * 1000 * 1000L)

namespace nanomark
{

#if defined(__linux__) || defined(Darwin)

static std::uint64_t nanoClock()
{
    struct timespec ts = {0, 0};

    clock_gettime(CLOCK_MONOTONIC_RAW, &ts);
    return (ts.tv_sec * 1000000000) + ts.tv_nsec;
}

#elif defined(WIN32)

static std::uint64_t nanoClock()
{
    static LARGE_INTEGER freq;
    static bool first = true;
    LARGE_INTEGER counter;

    if (first)
    {
        ::QueryPerformanceFrequency(&freq);
        first = false;
    }

    ::QueryPerformanceCounter(&counter);

    return (1000000000 * counter.QuadPart)/freq.QuadPart;
}

#endif

class Nanomark
{
public:
    virtual void setUp()
    {
    }

    virtual void tearDown()
    {
    }

    virtual void perThreadSetUp(std::size_t id)
    {
    }

    virtual void perThreadTearDown(std::size_t id)
    {
    }

    virtual uint64_t run() = 0;

    void name(const char *name)
    {
        m_name = name;
    }

    const char *name() const
    {
        return m_name;
    }

    void fixtureName(const char *fixtureName)
    {
        m_fixtureName = fixtureName;
    }

    const char *fixtureName() const
    {
        return m_fixtureName;
    }

    void iterationsPerRun(std::size_t iterationsPerRun)
    {
        m_iterationsPerRun = iterationsPerRun;
    }

    std::size_t iterationsPerRun() const
    {
        return m_iterationsPerRun;
    }

    void numberOfMaxThreads(std::size_t numberOfMaxThreads)
    {
        m_numberMaxThreads = numberOfMaxThreads;
    }

    std::size_t numberMaxThreads() const
    {
        return m_numberMaxThreads;
    }

    virtual void recordRun(std::size_t threadId, std::uint64_t measurementNs)
    {
    }

    virtual void recordRepetition(std::size_t threadId, std::uint64_t totalNs, std::size_t numberOfRuns)
    {
    }

protected:
    const char *m_name = "";
    const char *m_fixtureName = "";
    std::size_t m_iterationsPerRun = 1;
    std::size_t m_numberMaxThreads = 0;
};

class NanomarkRunner
{
public:
    static Nanomark *registerNanomark(
        const char *fixtureName,
        const char *name,
        std::size_t iterationsPer,
        Nanomark *impl)
    {
        impl->fixtureName(fixtureName);
        impl->name(name);

        table().push_back(impl);
        std::cout << "Registering " << fixtureName << "::" << name << std::endl;

        return impl;
    }

    static void run(
        Nanomark *nanomark,
        std::size_t numThreads,
        std::size_t repetitions,
        std::uint64_t minRunLengthNs)
    {
        nanomark->numberOfMaxThreads(numThreads);
        nanomark->setUp();

        for (std::size_t i = 0; i < repetitions; i++)
        {
            std::atomic<int> countDown(numThreads);
            std::atomic<std::size_t> threadId(0);
            std::atomic<bool> running(true);

            std::vector<std::thread> threads;

            for (std::size_t j = 0; j < numThreads; j++)
            {
                threads.emplace_back(std::thread([&]()
                {
                    std::uint64_t totalNs = 0, numberOfRuns = 0;
                    const std::size_t id = threadId.fetch_add(1);

                    nanomark->perThreadSetUp(id);

                    countDown--;
                    while (countDown > 0)
                    {
                        std::this_thread::yield();
                    }

                    while (running)
                    {
                        std::uint64_t elapsedNs = nanomark->run();

                        nanomark->recordRun(id, elapsedNs / nanomark->iterationsPerRun());

                        totalNs += elapsedNs / nanomark->iterationsPerRun();
                        numberOfRuns++;
                    }

                    nanomark->recordRepetition(id, totalNs, numberOfRuns);
                    nanomark->perThreadTearDown(id);
                }));
            }

            // wait for threads to get setup
            while (countDown > 0)
            {
                std::this_thread::yield();
            }

            std::uint64_t nowNs = nanoClock();
            std::uint64_t runStartNs = nowNs;

            while ((nowNs - runStartNs) < minRunLengthNs)
            {
                std::this_thread::sleep_for(std::chrono::milliseconds(500));
                nowNs = nanoClock();
            }

            running = false;

            for (std::thread &thr: threads)
            {
                thr.join();
            }
        }

        nanomark->tearDown();
    }

    static void run(
        std::size_t numThreads,
        std::size_t repetitions = DEFAULT_REPETITIONS,
        std::uint64_t minRunLengthNs = DEFAULT_RUN_LENGTH_NS)
    {
        for (auto nanomark : table())
        {
            run(nanomark, numThreads, repetitions, minRunLengthNs);
        }
    }

    static std::vector<Nanomark *> &table()
    {
        static std::vector<Nanomark *> table;
        return table;
    }
};

template <typename C, size_t N>
inline std::uint64_t runNanomark(C *obj)
{
    std::uint64_t start, end;

    start = nanoClock();
    for (int i = 0; i < N; i++)
    {
        obj->nanomarkBody();
    }
    end = nanoClock();
    return end - start;
}

template <typename C>
inline std::uint64_t runNanomark(C *obj)
{
    std::uint64_t start, end;

    start = nanoClock();
    obj->nanomarkBody();
    end = nanoClock();
    return end - start;
}

}

#define NANOMARK_CLASS_NAME(x,y) x##y

#define NANOMARK_N(c,r,i) \
    class NANOMARK_CLASS_NAME(c,r) : public c { \
    public: \
      NANOMARK_CLASS_NAME(c,r)() {}; \
      virtual std::uint64_t run() { return ::nanomark::runNanomark<NANOMARK_CLASS_NAME(c,r),i>(this); }; \
      void nanomarkBody(void); \
    private: \
      static ::nanomark::Nanomark *m_instance; \
    };                            \
    ::nanomark::Nanomark *NANOMARK_CLASS_NAME(c,r)::m_instance = ::nanomark::NanomarkRunner::registerNanomark(#c, #r, 1, new NANOMARK_CLASS_NAME(c,r)()); \
    inline void NANOMARK_CLASS_NAME(c,r)::nanomarkBody()

#define NANOMARK(c,r) \
    class NANOMARK_CLASS_NAME(c,r) : public c { \
    public: \
      NANOMARK_CLASS_NAME(c,r)() {}; \
      virtual std::uint64_t run() { return ::nanomark::runNanomark<NANOMARK_CLASS_NAME(c,r)>(this); }; \
      void nanomarkBody(void); \
    private: \
      static ::nanomark::Nanomark *m_instance; \
    };                            \
    ::nanomark::Nanomark *NANOMARK_CLASS_NAME(c,r)::m_instance = ::nanomark::NanomarkRunner::registerNanomark(#c, #r, 1, new NANOMARK_CLASS_NAME(c,r)()); \
    inline void NANOMARK_CLASS_NAME(c,r)::nanomarkBody()

#define NANOMARK_MAIN() \
    int main(int argc, char **argv) \
    { \
        NanomarkRunner::run(1); \
        return 0; \
    }

#endif //NANOMARK_H
