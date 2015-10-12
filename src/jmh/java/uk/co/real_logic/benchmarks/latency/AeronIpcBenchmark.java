/*
 * Copyright 2015 Real Logic Ltd.
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

import org.openjdk.jmh.annotations.*;
import uk.co.real_logic.aeron.*;
import uk.co.real_logic.aeron.driver.MediaDriver;
import uk.co.real_logic.aeron.driver.ThreadingMode;
import uk.co.real_logic.aeron.logbuffer.*;
import uk.co.real_logic.agrona.BitUtil;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.*;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static uk.co.real_logic.benchmarks.latency.Configuration.MAX_THREAD_COUNT;
import static uk.co.real_logic.benchmarks.latency.Configuration.RESPONSE_QUEUE_CAPACITY;

public class AeronIpcBenchmark
{
    public static final int STREAM_ID = 1;
    public static final int FRAGMENT_LIMIT = 128;
    public static final Integer SENTINEL = 0;

    @State(Scope.Benchmark)
    public static class SharedState
    {
        @Param({"1", "2", "10", "50", "100"})
        int burstLength;
        int[] values;

        final AtomicBoolean running = new AtomicBoolean(true);
        final AtomicInteger threadId = new AtomicInteger();

        MediaDriver.Context ctx;
        MediaDriver mediaDriver;
        Aeron aeron;
        Publication publication;
        Subscription subscription;

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

            values = new int[burstLength];
            for (int i = 0; i < burstLength; i++)
            {
                values[i] = -(burstLength - i);
            }

            ctx = new MediaDriver.Context()
                .threadingMode(ThreadingMode.SHARED)
                .sharedIdleStrategy(new NoOpIdleStrategy())
                .dirsDeleteOnStart(true);

            mediaDriver = MediaDriver.launch(ctx);
            aeron = Aeron.connect();
            publication = aeron.addPublication(CommonContext.IPC_CHANNEL, STREAM_ID);
            subscription = aeron.addSubscription(CommonContext.IPC_CHANNEL, STREAM_ID);

            consumerThread = new Thread(new Subscriber(subscription, running, responseQueues));

            consumerThread.setName("consumer");
            consumerThread.start();
        }

        @TearDown
        public synchronized void tearDown() throws Exception
        {
            running.set(false);
            consumerThread.join();

            aeron.close();
            mediaDriver.close();
            ctx.deleteAeronDirectory();
        }
    }

    @State(Scope.Thread)
    public static class PerThreadState
    {
        int id;
        int[] values;
        Publication publication;
        BufferClaim bufferClaim = new BufferClaim();
        Queue<Integer> responseQueue;

        @Setup
        public void setup(final SharedState sharedState)
        {
            id = sharedState.threadId.getAndIncrement();
            values = Arrays.copyOf(sharedState.values, sharedState.values.length);
            values[values.length - 1] = id;

            publication = sharedState.publication;
            responseQueue = sharedState.responseQueues[id];
        }
    }

    public static class Subscriber implements Runnable, FragmentHandler
    {
        private final Subscription subscription;
        private final AtomicBoolean running;
        private final Queue<Integer>[] responseQueues;

        public Subscriber(final Subscription subscription, final AtomicBoolean running, final Queue<Integer>[] responseQueues)
        {
            this.subscription = subscription;
            this.running = running;
            this.responseQueues = responseQueues;
        }

        public void run()
        {
            while (subscription.imageCount() == 0)
            {
                Thread.yield();
            }

            final Image image = subscription.images().get(0);

            while (true)
            {
                final int frameCount = subscription.poll(this, FRAGMENT_LIMIT);
                if (0 == frameCount && !running.get())
                {
                    break;
                }
            }
        }

        public void onFragment(final DirectBuffer buffer, final int offset, final int length, final Header header)
        {
            final int value = buffer.getInt(offset);
            if (value >= 0)
            {
                final Queue<Integer> responseQueue = responseQueues[value];
                while (!responseQueue.offer(SENTINEL))
                {
                    // busy spin
                }
            }
        }
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
        final BufferClaim bufferClaim = state.bufferClaim;
        final Publication publication = state.publication;

        for (final int value : state.values)
        {
            while (publication.tryClaim(BitUtil.SIZE_OF_INT, bufferClaim) < 0)
            {
                // busy spin
            }

            final int offset = bufferClaim.offset();
            bufferClaim.buffer().putInt(offset, value);

            bufferClaim.commit();
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
