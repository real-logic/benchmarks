/*
 * Copyright 2015-2023 Real Logic Limited.
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

import io.aeron.Aeron;
import io.aeron.CommonContext;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.OneToOneConcurrentArrayQueue;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.hints.ThreadHints;
import org.openjdk.jmh.annotations.*;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static uk.co.real_logic.benchmarks.aeron.ipc.Configuration.MAX_THREAD_COUNT;
import static uk.co.real_logic.benchmarks.aeron.ipc.Configuration.RESPONSE_QUEUE_CAPACITY;

public class AeronIpcBenchmark
{
    static final int STREAM_ID = 1;
    static final int FRAGMENT_LIMIT = 128;
    static final Integer SENTINEL = 0;

    @State(Scope.Benchmark)
    public static class SharedState
    {
        @Param({ "1", "100" })
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
                .termBufferSparseFile(false)
                .threadingMode(ThreadingMode.SHARED)
                .sharedIdleStrategy(new BusySpinIdleStrategy())
                .dirDeleteOnStart(true);

            mediaDriver = MediaDriver.launch(ctx);
            aeron = Aeron.connect(new Aeron.Context().preTouchMappedMemory(true));
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
        Queue<Integer> responseQueue;
        final UnsafeBuffer buffer = new UnsafeBuffer(BufferUtil.allocateDirectAligned(128, 128));

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

        Subscriber(final Subscription subscription, final AtomicBoolean running, final Queue<Integer>[] responseQueues)
        {
            this.subscription = subscription;
            this.running = running;
            this.responseQueues = responseQueues;
        }

        public void run()
        {
            while (!subscription.isConnected())
            {
                Thread.yield();
            }

            final IdleStrategy idleStrategy = new BusySpinIdleStrategy();
            while (true)
            {
                final int frameCount = subscription.poll(this, FRAGMENT_LIMIT);
                if (0 == frameCount)
                {
                    if (!running.get())
                    {
                        break;
                    }
                }

                idleStrategy.idle(frameCount);
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
                    ThreadHints.onSpinWait();
                }
            }
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
        final UnsafeBuffer buffer = state.buffer;
        final Publication publication = state.publication;

        for (final int value : state.values)
        {
            buffer.putInt(0, value);
            while (publication.offer(buffer, 0, SIZE_OF_INT) < 0)
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
