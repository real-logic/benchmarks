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

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static java.lang.Math.round;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

class AsyncProgressReporter implements ProgressReporter
{
    private static final long NANOS_PER_SECOND = SECONDS.toNanos(1);
    private static final long SLEEP_NANOS = MILLISECONDS.toNanos(100);
    private final AtomicReference<Runnable> reportTask = new AtomicReference<>();
    private final PrintStream out;

    AsyncProgressReporter(final PrintStream out)
    {
        this.out = out;
        final Thread t = new Thread(this::runTask, "progress-reporter");
        t.setDaemon(true);
        t.start();
    }

    public void reportProgress(final long startTimeNs, final long nowNs, final long sentMessages, final int iterations)
    {
        reportTask.set(() ->
        {
            final long elapsedSeconds = round((double)(nowNs - startTimeNs) / NANOS_PER_SECOND);
            final long sendRate = 0 == elapsedSeconds ? sentMessages : sentMessages / elapsedSeconds;
            out.format(
                "Send rate: %,d msgs/sec (%d of %d)%n", sendRate, 0 == elapsedSeconds ? 1 : elapsedSeconds, iterations);
        });
    }

    private void runTask()
    {
        while (true)
        {
            final Runnable task = reportTask.get();
            if (task != null)
            {
                task.run();
                reportTask.compareAndSet(task, null);
            }
            LockSupport.parkNanos(SLEEP_NANOS);
        }
    }
}