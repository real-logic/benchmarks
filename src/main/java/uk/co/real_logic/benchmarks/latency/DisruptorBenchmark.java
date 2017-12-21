/*
 * Copyright 2015-2017 Real Logic Ltd.
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
package uk.co.real_logic.benchmarks.latency;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.openjdk.jmh.annotations.*;
import org.agrona.concurrent.OneToOneConcurrentArrayQueue;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static uk.co.real_logic.benchmarks.latency.Configuration.MAX_THREAD_COUNT;
import static uk.co.real_logic.benchmarks.latency.Configuration.RESPONSE_QUEUE_CAPACITY;

public class DisruptorBenchmark
{
    static final Integer SENTINEL = 0;

    @State(Scope.Benchmark)
    public static class SharedState
    {
        @Param({ "1", "100" })
        int burstLength;
        int[] values;

        final AtomicInteger threadId = new AtomicInteger();

        Disruptor<Message> disruptor;
        Handler handler;

        @SuppressWarnings("unchecked")
        final Queue<Integer>[] responseQueues = new OneToOneConcurrentArrayQueue[MAX_THREAD_COUNT];

        @Setup
        public synchronized void setup() throws InterruptedException
        {
            for (int i = 0; i < MAX_THREAD_COUNT; i++)
            {
                responseQueues[i] = new OneToOneConcurrentArrayQueue<>(RESPONSE_QUEUE_CAPACITY);
            }

            values = new int[burstLength];
            for (int i = 0; i < burstLength; i++)
            {
                values[i] = -(burstLength - i);
            }

            handler = new Handler(responseQueues);

            disruptor = new Disruptor<>(
                Message::new,
                Configuration.SEND_QUEUE_CAPACITY,
                (ThreadFactory)Thread::new,
                ProducerType.MULTI,
                new BusySpinWaitStrategy());

            disruptor.handleEventsWith(handler);
            disruptor.start();

            handler.waitForStart();
        }

        @TearDown
        public synchronized void tearDown() throws Exception
        {
            disruptor.shutdown();
            handler.waitForShutdown();

            System.gc();
        }
    }

    @State(Scope.Thread)
    public static class PerThreadState
    {
        int id;
        int[] values;
        Queue<Integer> responseQueue;
        RingBuffer<Message> ringBuffer;

        @Setup
        public void setup(final SharedState sharedState)
        {
            id = sharedState.threadId.getAndIncrement();
            values = Arrays.copyOf(sharedState.values, sharedState.values.length);
            values[values.length - 1] = id;

            responseQueue = sharedState.responseQueues[id];
            ringBuffer = sharedState.disruptor.getRingBuffer();
        }
    }

    public static class Handler implements EventHandler<Message>, LifecycleAware
    {
        private final Queue<Integer>[] responseQueues;
        private final CountDownLatch startLatch = new CountDownLatch(1);
        private final CountDownLatch stopLatch = new CountDownLatch(1);

        Handler(final Queue<Integer>[] responseQueues)
        {
            this.responseQueues = responseQueues;
        }

        public void onEvent(final Message event, final long sequence, final boolean endOfBatch)
        {
            int value = event.value;
            if (value >= 0)
            {
                final Queue<Integer> responseQueue = responseQueues[value];
                while (!responseQueue.offer(SENTINEL))
                {
                    // busy spin
                }
            }

            event.value = -1;
        }

        public void onStart()
        {
            startLatch.countDown();
        }

        public void onShutdown()
        {
            stopLatch.countDown();
        }

        public void waitForStart() throws InterruptedException
        {
            startLatch.await();
        }

        public void waitForShutdown() throws InterruptedException
        {
            stopLatch.await();
        }
    }

    static class Message
    {
        int value = -1;
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @Threads(1)
    public Integer test1Producer(final PerThreadState state)
    {
        return sendBurst(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @Threads(2)
    public Integer test2Producers(final PerThreadState state)
    {
        return sendBurst(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @Threads(3)
    public Integer test3Producers(final PerThreadState state)
    {
        return sendBurst(state);
    }

    private Integer sendBurst(final PerThreadState state)
    {
        final RingBuffer<Message> ringBuffer = state.ringBuffer;

        for (final int value : state.values)
        {
            ringBuffer.publishEvent((m, s, i) -> m.value = i, value);
        }

        Integer value;
        do
        {
            value = state.responseQueue.poll();
        }
        while (null == value);

        return value;
    }
}