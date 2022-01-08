/*
 * Copyright 2015-2022 Real Logic Limited.
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

import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.OneToOneConcurrentArrayQueue;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.agrona.hints.ThreadHints;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static uk.co.real_logic.benchmarks.aeron.ipc.Configuration.MAX_THREAD_COUNT;
import static uk.co.real_logic.benchmarks.aeron.ipc.Configuration.RESPONSE_QUEUE_CAPACITY;

public class ManyToOneRingBufferBenchmark
{
    static final int MESSAGE_COUNT_LIMIT = 16;
    static final Integer SENTINEL = 0;
    static final int BUFFER_LENGTH = (64 * 1024) + RingBufferDescriptor.TRAILER_LENGTH;

    @State(Scope.Benchmark)
    public static class SharedState
    {
        @Param({ "1", "100" })
        int burstLength;
        int[] values;

        final AtomicBoolean running = new AtomicBoolean(true);
        final AtomicInteger threadId = new AtomicInteger();

        RingBuffer ringBuffer;

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

            ringBuffer = new ManyToOneRingBuffer(new UnsafeBuffer(ByteBuffer.allocateDirect(BUFFER_LENGTH)));

            consumerThread = new Thread(new Subscriber(ringBuffer, running, responseQueues));

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
        int[] values;
        RingBuffer ringBuffer;
        Queue<Integer> responseQueue;
        final UnsafeBuffer tempBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(8));

        @Setup
        public void setup(final SharedState sharedState)
        {
            id = sharedState.threadId.getAndIncrement();
            values = Arrays.copyOf(sharedState.values, sharedState.values.length);
            values[values.length - 1] = id;

            ringBuffer = sharedState.ringBuffer;
            responseQueue = sharedState.responseQueues[id];
        }
    }

    public static class Subscriber implements Runnable, MessageHandler
    {
        private final RingBuffer ringBuffer;
        private final AtomicBoolean running;
        private final Queue<Integer>[] responseQueues;

        Subscriber(final RingBuffer ringBuffer, final AtomicBoolean running, final Queue<Integer>[] responseQueues)
        {
            this.ringBuffer = ringBuffer;
            this.running = running;
            this.responseQueues = responseQueues;
        }

        public void run()
        {
            while (true)
            {
                final int msgCount = ringBuffer.read(this, MESSAGE_COUNT_LIMIT);
                if (0 == msgCount && !running.get())
                {
                    break;
                }
            }
        }

        public void onMessage(final int msgTypeId, final MutableDirectBuffer buffer, final int index, final int length)
        {
            final int value = buffer.getInt(index);
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
        final RingBuffer ringBuffer = state.ringBuffer;
        final UnsafeBuffer tempBuffer = state.tempBuffer;

        for (final Integer value : state.values)
        {
            tempBuffer.putInt(0, value);
            while (!ringBuffer.write(1, tempBuffer, 0, BitUtil.SIZE_OF_INT))
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
