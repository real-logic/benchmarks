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
package uk.co.real_logic.benchmarks.remote;

import org.agrona.concurrent.OneToOneConcurrentArrayQueue;
import org.junit.jupiter.api.Test;

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AsyncProgressReporterTest
{
    private final PrintStream out = mock(PrintStream.class);
    private final OneToOneConcurrentArrayQueue<Runnable> tasks = new OneToOneConcurrentArrayQueue<>(256);
    private final AsyncProgressReporter reporter = new AsyncProgressReporter(out, tasks);

    @Test
    void shouldReportProgressWhenElapsedSecondsIsZero()
    {
        final AtomicReference<Thread> workerThread = new AtomicReference<>();
        doAnswer(invocation ->
        {
            workerThread.set(Thread.currentThread());
            return null;
        }).when(out).format(anyString(), anyLong(), anyLong(), anyInt());

        final int iterations = 3;
        reporter.reportProgress(1000, 1000, 55, iterations);

        while (null == workerThread.get())
        {
            Thread.yield();
        }
        assertNotEquals(Thread.currentThread(), workerThread.get());
        verify(out).format("Send rate: %,d msgs/sec (%d of %d)%n", 55L, 1L, iterations);
    }

    @Test
    void shouldCountIterations()
    {
        final AtomicReference<Thread> workerThread = new AtomicReference<>();
        doAnswer(invocation ->
        {
            workerThread.set(Thread.currentThread());
            return null;
        }).when(out).format(anyString(), anyLong(), anyLong(), anyInt());

        final int iterations = 11;
        reporter.reportProgress(TimeUnit.SECONDS.toNanos(3), TimeUnit.SECONDS.toNanos(7), 33, iterations);

        while (null == workerThread.get())
        {
            Thread.yield();
        }
        assertNotEquals(Thread.currentThread(), workerThread.get());
        verify(out).format("Send rate: %,d msgs/sec (%d of %d)%n", 8L, 4L, iterations);
    }

    @Test
    void shouldCompleteAllTasks()
    {
        final AtomicInteger invocationCount = new AtomicInteger();
        doAnswer(invocation ->
        {
            invocationCount.getAndIncrement();
            return null;
        }).when(out).format(anyString(), anyLong(), anyLong(), anyInt());

        final int iterations = tasks.capacity();
        for (int i = 0; i < iterations; i++)
        {
            reporter.reportProgress(TimeUnit.SECONDS.toNanos(i), TimeUnit.SECONDS.toNanos(i + 100), 4, 5);
        }

        while (invocationCount.get() != iterations)
        {
            Thread.yield();
        }
        verify(out, times(iterations)).format(anyString(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void resetWaitsForAllTasksToComplete()
    {
        final int samples = 10;
        final AtomicInteger completedTasks = new AtomicInteger();
        for (int i = 0; i < samples; i++)
        {
            assertTrue(tasks.offer(
                () ->
                {
                    LockSupport.parkNanos(1_000_000);
                    completedTasks.getAndIncrement();
                }));
        }

        reporter.reset();

        assertEquals(samples, completedTasks.get());
        assertTrue(tasks.isEmpty());
    }
}
