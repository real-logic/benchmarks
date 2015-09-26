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

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import uk.co.real_logic.agrona.concurrent.OneToOneConcurrentArrayQueue;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static uk.co.real_logic.benchmarks.latency.Configuration.MAX_THREAD_COUNT;
import static uk.co.real_logic.benchmarks.latency.Configuration.RESPONSE_QUEUE_CAPACITY;
import static uk.co.real_logic.benchmarks.latency.Configuration.SEND_QUEUE_CAPACITY;

public class DisruptorOptimisedBenchmark
{
    public static final Integer SENTINEL = 0;

    @State(Scope.Benchmark)
    public static class SharedState
    {
        @Param({"1", "2", "10", "50", "100"})
        int burstLength;
        int[] values;

        final AtomicInteger threadId = new AtomicInteger();

        IntRingBuffer buffer;
        Handler handler;
        BatchEventProcessor<IntRingBuffer.IntEvent> processor;

        @SuppressWarnings("unchecked")
        final Queue<Integer>[] responseQueues = new OneToOneConcurrentArrayQueue[MAX_THREAD_COUNT];
        Thread consumerThread;

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

            Sequencer sequencer = new MultiProducerSequencer(SEND_QUEUE_CAPACITY, new YieldingWaitStrategy());
            buffer = new IntRingBuffer(sequencer);
            processor = buffer.createProcessor(handler);

            consumerThread = new Thread(processor);
            consumerThread.start();
        }

        @TearDown
        public synchronized void tearDown() throws Exception
        {
            processor.halt();

            System.gc();
        }
    }

    @State(Scope.Thread)
    public static class PerThreadState
    {
        int id;
        int[] values;
        Queue<Integer> responseQueue;
        IntRingBuffer buffer;

        @Setup
        public void setup(final SharedState sharedState)
        {
            id = sharedState.threadId.getAndIncrement();
            values = Arrays.copyOf(sharedState.values, sharedState.values.length);
            values[values.length - 1] = id;

            responseQueue = sharedState.responseQueues[id];
            buffer = sharedState.buffer;
        }
    }

    public static class Handler implements IntRingBuffer.IntHandler, LifecycleAware
    {
        private final Queue<Integer>[] responseQueues;
        private final CountDownLatch startLatch = new CountDownLatch(1);
        private final CountDownLatch stopLatch = new CountDownLatch(1);

        public Handler(final Queue<Integer>[] responseQueues)
        {
            this.responseQueues = responseQueues;
        }

        @Override
        public void onEvent(final int value, final long sequence, final boolean endOfBatch)
        {
            if (value >= 0)
            {
                responseQueues[value].offer(SENTINEL);
            }
        }

        @Override
        public void onStart()
        {
            startLatch.countDown();
        }

        @Override
        public void onShutdown()
        {
            stopLatch.countDown();
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
        state.buffer.put(state.values);
//        for (Integer value : state.values)
//        {
//            state.buffer.put(value);
//        }

        Integer value;
        do
        {
            value = state.responseQueue.poll();
        }
        while (null == value);

        return value;
    }

    public static void main(String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(DisruptorOptimisedBenchmark.class.getSimpleName())
            .forks(0)
            .build();
        new Runner(opt).run();
    }

    private static class IntRingBuffer
    {
        private final Sequencer sequencer;
        private final int[] buffer;
        private final int mask;

        public IntRingBuffer(final Sequencer sequencer)
        {
            this.sequencer = sequencer;
            this.buffer = new int[sequencer.getBufferSize()];
            this.mask = sequencer.getBufferSize() - 1;
        }

        private int index(final long sequence)
        {
            return (int) sequence & mask;
        }

        public void put(final int e)
        {
            final long next = sequencer.next();
            buffer[index(next)] = e;
            sequencer.publish(next);
        }

        public void put(final int[] es)
        {
            final long hi = sequencer.next(es.length);
            final long lo = hi - (es.length - 1);

            for (int i = 0; i < es.length; i++)
            {
                buffer[index(lo + i)] = es[i];
            }

            sequencer.publish(lo, hi);
        }

        public interface IntHandler
        {
            void onEvent(int value, long sequence, boolean endOfBatch);
        }

        private class IntEvent implements DataProvider<IntEvent>
        {
            private long sequence;

            public int get()
            {
                return buffer[index(sequence)];
            }

            @Override
            public IntEvent get(final long sequence)
            {
                this.sequence = sequence;
                return this;
            }
        }

        public BatchEventProcessor<IntEvent> createProcessor(final IntHandler handler)
        {
            return new BatchEventProcessor<>(
                new IntEvent(),
                sequencer.newBarrier(),
                (event, sequence, endOfBatch) -> handler.onEvent(event.get(), sequence, endOfBatch));
        }
    }
}
