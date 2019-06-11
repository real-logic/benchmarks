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
#include <atomic>

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

extern "C"
{
#include "hdr_histogram.h"
}

namespace nanomark
{

#if defined(Darwin)

static std::uint64_t nanoClock()
{
    struct timespec ts = {0, 0};

    clock_gettime(CLOCK_MONOTONIC_RAW, &ts);
    return (ts.tv_sec * 1000000000) + ts.tv_nsec;
}

#elif defined(__linux__)

static std::uint64_t nanoClock()
{
    struct timespec ts = {0, 0};

    clock_gettime(CLOCK_MONOTONIC, &ts);
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
        if (!m_histograms.empty())
        {
            for (auto histogram : m_histograms)
            {
                hdr_close(histogram);
            }

            m_histograms.clear();
        }

        if (nullptr != m_combinedHistogram)
        {
            hdr_close(m_combinedHistogram);
            m_combinedHistogram = nullptr;
        }

        for (std::size_t i = 0; i < numberMaxThreads(); i++)
        {
            hdr_histogram *histogram = nullptr;

            hdr_init(1, 10 * 1000 * 1000 * 1000LL, 3, &histogram);
            m_histograms.push_back(histogram);
        }
    }

    virtual void tearDown()
    {
        hdr_histogram *combinedHistogram;

        hdr_init(1, 10 * 1000 * 1000 * 1000LL, 3, &combinedHistogram);

        for (std::size_t i = 0; i < numberMaxThreads(); i++)
        {
            hdr_histogram *histogram = m_histograms[i];

            if (nullptr != histogram)
            {
                hdr_add(combinedHistogram, histogram);
            }
        }

        m_combinedHistogram = combinedHistogram;
    }

    virtual void perThreadSetUp(std::size_t id, std::size_t repetition)
    {
    }

    virtual void perThreadTearDown(std::size_t id, std::size_t repetition)
    {
    }

    virtual uint64_t run(std::size_t id) = 0;

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

    void numberOfMaxRepetitions(std::size_t numberOfMaxRepetitions)
    {
        m_numberMaxRepetitions = numberOfMaxRepetitions;
    }

    std::size_t numberOfMaxRepetitions()
    {
        return m_numberMaxRepetitions;
    }

    virtual void recordRun(std::size_t threadId, std::uint64_t measurementNs)
    {
        hdr_record_value(m_histograms[threadId], (std::int64_t)measurementNs);
    }

    virtual void recordRepetition(
        std::size_t threadId, std::size_t repetition, std::uint64_t totalNs, std::size_t numberOfRuns)
    {
    }

    hdr_histogram *histogram()
    {
        return m_combinedHistogram;
    }

    hdr_histogram *histogram(std::size_t threadId)
    {
        return m_histograms[threadId];
    }

    void printFullHistogram()
    {
        if (nullptr != m_combinedHistogram)
        {
            hdr_percentiles_print(m_combinedHistogram, stdout, 5, 1.0, CLASSIC);
            fflush(stdout);
        }
    }

    static std::string histogramSummary(hdr_histogram *histogram)
    {
        std::ostringstream ostream;

        ostream << "min/mean/max = " <<
            std::to_string(hdr_min(histogram)) << "/" <<
            std::to_string(hdr_mean(histogram)) << "/" <<
            std::to_string(hdr_max(histogram)) << " ns, ";
        ostream << "p0.50/p0.90/p0.99 = " <<
            std::to_string(hdr_value_at_percentile(histogram, 50.0)) << "/" <<
            std::to_string(hdr_value_at_percentile(histogram, 90.0)) << "/" <<
            std::to_string(hdr_value_at_percentile(histogram, 99.0)) << " ns";

        return ostream.str();
    }

protected:
    const char *m_name = "";
    const char *m_fixtureName = "";
    std::size_t m_iterationsPerRun = 1;
    std::size_t m_numberMaxThreads = 0;
    std::size_t m_numberMaxRepetitions = 0;
    std::vector<hdr_histogram*> m_histograms;
    hdr_histogram *m_combinedHistogram = nullptr;
};

class NanomarkRunner
{
public:
    static Nanomark *registerNanomark(
        const char *fixtureName,
        const char *name,
        std::size_t iterationsPerRun,
        Nanomark *impl)
    {
        impl->fixtureName(fixtureName);
        impl->name(name);
        impl->iterationsPerRun(iterationsPerRun);

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
        std::cout << "Running " << nanomark->fixtureName() << "::" << nanomark->name() << std::endl;

        nanomark->numberOfMaxThreads(numThreads);
        nanomark->numberOfMaxRepetitions(repetitions);
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

                    nanomark->perThreadSetUp(id, i);

                    countDown--;
                    while (countDown > 0)
                    {
                        std::this_thread::yield();
                    }

                    while (running)
                    {
                        std::uint64_t elapsedNs = nanomark->run(id);

                        nanomark->recordRun(id, elapsedNs / nanomark->iterationsPerRun());

                        totalNs += elapsedNs / nanomark->iterationsPerRun();
                        numberOfRuns++;
                    }

                    nanomark->recordRepetition(id, i, totalNs, numberOfRuns);
                    nanomark->perThreadTearDown(id, i);
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
inline std::uint64_t runNanomark(C *obj, std::size_t id)
{
    std::uint64_t start, end;

    start = nanoClock();
    for (std::size_t i = 0; i < N; i++)
    {
        obj->nanomarkBody(id);
    }
    end = nanoClock();
    return end - start;
}

template <typename C>
inline std::uint64_t runNanomark(C *obj, std::size_t id)
{
    std::uint64_t start, end;

    start = nanoClock();
    obj->nanomarkBody(id);
    end = nanoClock();
    return end - start;
}

}

#define NANOMARK_CLASS_NAME(x,y) x##y

#define NANOMARK_N(c,r,i) \
    class NANOMARK_CLASS_NAME(c,r) : public c { \
    public: \
      NANOMARK_CLASS_NAME(c,r)() {}; \
      virtual std::uint64_t run(std::size_t id) { return ::nanomark::runNanomark<NANOMARK_CLASS_NAME(c,r),i>(this, id); }; \
      void nanomarkBody(std::size_t id); \
    private: \
      static ::nanomark::Nanomark *m_instance; \
    };                            \
    ::nanomark::Nanomark *NANOMARK_CLASS_NAME(c,r)::m_instance = ::nanomark::NanomarkRunner::registerNanomark(#c, #r, i, new NANOMARK_CLASS_NAME(c,r)()); \
    inline void NANOMARK_CLASS_NAME(c,r)::nanomarkBody

#define NANOMARK(c,r) \
    class NANOMARK_CLASS_NAME(c,r) : public c { \
    public: \
      NANOMARK_CLASS_NAME(c,r)() {}; \
      virtual std::uint64_t run(std::size_t id) { return ::nanomark::runNanomark<NANOMARK_CLASS_NAME(c,r)>(this, id); }; \
      void nanomarkBody(std::size_t id); \
    private: \
      static ::nanomark::Nanomark *m_instance; \
    };                            \
    ::nanomark::Nanomark *NANOMARK_CLASS_NAME(c,r)::m_instance = ::nanomark::NanomarkRunner::registerNanomark(#c, #r, 1, new NANOMARK_CLASS_NAME(c,r)()); \
    inline void NANOMARK_CLASS_NAME(c,r)::nanomarkBody

#define NANOMARK_MAIN() \
    int main(int argc, char **argv) \
    { \
        NanomarkRunner::run(1); \
        return 0; \
    }

#endif //NANOMARK_H
