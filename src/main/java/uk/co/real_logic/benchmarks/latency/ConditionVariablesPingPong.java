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

import org.HdrHistogram.Histogram;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.out;

public class ConditionVariablesPingPong
{
    private static final int REPETITIONS = 2_000_000;

    private static final Lock pingLock = new ReentrantLock();
    private static final Condition pingCondition = pingLock.newCondition();

    private static final Lock pongLock = new ReentrantLock();
    private static final Condition pongCondition = pongLock.newCondition();

    private static final Histogram histogram = new Histogram(3);

    private static long pingValue = -1;
    private static long pongValue = -1;

    public static void main(final String[] args) throws Exception
    {
        for (int i = 0; i < 5; i++)
        {
            testRun();
        }
    }

    private static void testRun() throws InterruptedException
    {
        pingValue = -1;
        pongValue = -1;
        histogram.reset();

        final Thread sendThread = new Thread(new PingRunner());
        final Thread echoThread = new Thread(new PongRunner());
        echoThread.start();
        sendThread.start();

        echoThread.join();

        out.println("pingValue = " + pingValue + ", pongValue = " + pongValue);
        System.out.println("Histogram of RTT latencies in microseconds.");

        histogram.outputPercentileDistribution(System.out, 1000.0);
    }

    public static class PingRunner implements Runnable
    {
        public void run()
        {
            for (long i = 0; i < REPETITIONS; i++)
            {
                final long start = System.nanoTime();

                pingLock.lock();
                try
                {
                    pingValue = i;
                    pingCondition.signal();
                }
                finally
                {
                    pingLock.unlock();
                }

                pongLock.lock();
                try
                {
                    while (pongValue != i)
                    {
                        pongCondition.await();
                    }
                }
                catch (final InterruptedException ex)
                {
                    break;
                }
                finally
                {
                    pongLock.unlock();
                }

                final long duration = System.nanoTime() - start;
                histogram.recordValue(duration);
            }
        }
    }

    public static class PongRunner implements Runnable
    {
        public void run()
        {
            for (long i = 0; i < REPETITIONS; i++)
            {
                pingLock.lock();
                try
                {
                    while (pingValue != i)
                    {
                        pingCondition.await();
                    }
                }
                catch (final InterruptedException ex)
                {
                    break;
                }
                finally
                {
                    pingLock.unlock();
                }

                pongLock.lock();
                try
                {
                    pongValue = i;
                    pongCondition.signal();
                }
                finally
                {
                    pongLock.unlock();
                }
            }
        }
    }
}

