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
package uk.co.real_logic.benchmarks.aeron.ipc;

import org.agrona.concurrent.OneToOneConcurrentArrayQueue;
import org.agrona.hints.ThreadHints;
import org.openjdk.jmh.annotations.*;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static uk.co.real_logic.benchmarks.aeron.ipc.Configuration.MAX_THREAD_COUNT;
import static uk.co.real_logic.benchmarks.aeron.ipc.Configuration.RESPONSE_QUEUE_CAPACITY;

public class LinkedBlockingQueueBenchmark
{
    @State(Scope.Benchmark)
    public static class SharedState
    {
        @Param({ "1", "100" })
        int burstLength;
        Integer[] values;

        final AtomicBoolean running = new AtomicBoolean(true);
        final AtomicInteger threadId = new AtomicInteger();
        final Queue<Integer> sendQueue = new LinkedBlockingQueue<>();

        @SuppressWarnings("unchecked")
        final Queue<Integer>[] responseQueues = new OneToOneConcurrentArrayQueue[MAX_THREAD_COUNT];

        Thread consumerThread;

        @Setup
        public synchronized void setup()
        {
            for (int i = 0; i < MAX_THREAD_COUNT; i++)
            {
                responseQueues[i] = new OneToOneConcurrentArrayQueue<>(RESPONSE_QUEUE_CAPACITY);
            }

            values = new Integer[burstLength];
            for (int i = 0; i < burstLength; i++)
            {
                values[i] = -(burstLength - i);
            }

            consumerThread = new Thread(
                () ->
                {
                    while (true)
                    {
                        final Integer value = sendQueue.poll();
                        if (null == value)
                        {
                            if (!running.get())
                            {
                                break;
                            }

                            ThreadHints.onSpinWait();
                        }
                        else
                        {
                            final int intValue = value;
                            if (intValue >= 0)
                            {
                                final Queue<Integer> responseQueue = responseQueues[value];
                                while (!responseQueue.offer(value))
                                {
                                    ThreadHints.onSpinWait();
                                }
                            }
                        }
                    }
                }
            );

            consumerThread.setName("consumer");
            consumerThread.start();
        }

        @TearDown
        public synchronized void tearDown() throws Exception
        {
            running.set(false);
            consumerThread.join();
        }
    }

    @State(Scope.Thread)
    public static class PerThreadState
    {
        int id;
        Integer[] values;
        Queue<Integer> sendQueue;
        Queue<Integer> responseQueue;

        @Setup
        public void setup(final SharedState sharedState)
        {
            id = sharedState.threadId.getAndIncrement();
            values = Arrays.copyOf(sharedState.values, sharedState.values.length);
            values[values.length - 1] = id;

            sendQueue = sharedState.sendQueue;
            responseQueue = sharedState.responseQueues[id];
        }
    }

    @Benchmark
    @BenchmarkMode({ Mode.SampleTime, Mode.AverageTime })
    @Threads(1)
    public Integer test1Producer(final PerThreadState state)
    {
        return sendBurst(state);
    }

    @Benchmark
    @BenchmarkMode({ Mode.SampleTime, Mode.AverageTime })
    @Threads(2)
    public Integer test2Producers(final PerThreadState state)
    {
        return sendBurst(state);
    }

    @Benchmark
    @BenchmarkMode({ Mode.SampleTime, Mode.AverageTime })
    @Threads(3)
    public Integer test3Producers(final PerThreadState state)
    {
        return sendBurst(state);
    }

    private Integer sendBurst(final PerThreadState state)
    {
        final Queue<Integer> sendQueue = state.sendQueue;

        for (final Integer value : state.values)
        {
            while (!sendQueue.offer(value))
            {
                ThreadHints.onSpinWait();
            }
        }

        Integer value;
        while (true)
        {
            value = state.responseQueue.poll();
            if (value != null)
            {
                break;
            }

            ThreadHints.onSpinWait();
        }

        return value;
    }
}
