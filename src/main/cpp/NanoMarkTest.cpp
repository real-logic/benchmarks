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

#include "NanoMark.h"

class TestFixture : public nanomark::Nanomark
{
public:
    void setUp() override
    {
        std::cout << "SetUp" << std::endl;
    }

    void tearDown() override
    {
        std::cout << "TearDown" << std::endl;
    }

    void perThreadSetUp(std::size_t id) override
    {
        std::cout << "Thread SetUp " << std::to_string(id) << std::endl;
    }

    void perThreadTearDown(std::size_t id) override
    {
        std::cout << "Thread TearDown " << std::to_string(id) << std::endl;
    }

    void recordRun(std::size_t id, std::uint64_t measurementNs) override
    {
        if (first)
        {
            std::cout << "Measurement " << std::to_string(measurementNs) << std::endl;
            first = false;
        }
    }

    void recordRepetition(std::size_t id, std::uint64_t totalNs, std::size_t numberOfRuns) override
    {
        std::cout << "Repetition " << std::to_string(id) << " " <<
            std::to_string(totalNs) << " " <<
            std::to_string(numberOfRuns) << std::endl;
        std::cout << "nanos/op " << std::to_string((double)totalNs / (double)numberOfRuns) << std::endl;
    }

    bool first = true;
};

NANOMARK(TestFixture, runTest)
{
    std::string someSillyString("my silly string");
}

int main(int argc, char **argv)
{
    ::nanomark::NanomarkRunner::run(1);
    ::nanomark::NanomarkRunner::run(2);
    return 0;
}
