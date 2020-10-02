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

#include <iostream>

#include "NanoMark.h"

class TestFixture : public nanomark::Nanomark
{
public:
    void setUp() override
    {
        Nanomark::setUp();
        std::cout << "SetUp" << std::endl;
    }

    void tearDown() override
    {
        Nanomark::tearDown();
        std::cout << "TearDown: " << histogramSummary(histogram()) << std::endl;

        printFullHistogram();
    }

    void perThreadSetUp(std::size_t id, std::size_t repetition) override
    {
        std::ostringstream stream;

        stream << "Thread " << std::to_string(id) << " SetUp " <<
            std::to_string(repetition + 1) << "/" << numberOfMaxRepetitions() << std::endl;
        std::cout << stream.str();
    }

    void perThreadTearDown(std::size_t id, std::size_t repetition) override
    {
        std::ostringstream stream;

        stream << "Thread " << std::to_string(id) << " TearDown " <<
            std::to_string(repetition + 1) << "/" << numberOfMaxRepetitions() <<
            ": " << histogramSummary(histogram(id)) << std::endl;
        std::cout << stream.str();
    }

    void recordRun(std::size_t id, std::uint64_t measurementNs) override
    {
        Nanomark::recordRun(id, measurementNs);

        if (first)
        {
            std::ostringstream stream;

            stream << "Thread " << std::to_string(id) << " First Measurement "
                   << std::to_string(measurementNs) << std::endl;
            std::cout << stream.str();
            first = false;
        }
    }

    void recordRepetition(
        std::size_t id, std::size_t repetition, std::uint64_t totalNs, std::size_t numberOfRuns) override
    {
        std::ostringstream stream;

        Nanomark::recordRepetition(id, repetition, totalNs, numberOfRuns);
        stream << "Thread " << std::to_string(id) << " repetition " << std::to_string(repetition + 1) << ": "
               << "nanos/op " << std::to_string((double)totalNs / (double)numberOfRuns) << " "
               << histogramSummary(histogram(id)) << std::endl;

        std::cout << stream.str();
    }

    bool first = true;
};

NANOMARK(TestFixture, runTest)(std::size_t)
{
    std::string someSillyString("my silly string");
}

int main(int argc, char **argv)
{
    ::nanomark::NanomarkRunner::run(1);
    ::nanomark::NanomarkRunner::run(2, 5);

    return 0;
}
