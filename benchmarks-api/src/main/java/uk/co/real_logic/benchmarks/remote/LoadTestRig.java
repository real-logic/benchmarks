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
package uk.co.real_logic.benchmarks.remote;

import org.agrona.LangUtil;
import org.agrona.PropertyAction;
import org.agrona.SystemUtil;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NanoClock;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Math.min;
import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static uk.co.real_logic.benchmarks.remote.MessageTransceiverBase.CHECKSUM;

/**
 * {@code LoadTestRig} class is the core of the RTT benchmark. It is responsible for running benchmark against provided
 * {@link MessageTransceiver} instance using given {@link Configuration}.
 */
public final class LoadTestRig
{
    static final int MINIMUM_NUMBER_OF_CPU_CORES = 6;

    private static final long NANOS_PER_SECOND = SECONDS.toNanos(1);
    private static final long RECEIVE_DEADLINE_NS = SECONDS.toNanos(30);
    private final Configuration configuration;
    private final MessageTransceiver messageTransceiver;
    private final PrintStream out;
    private final NanoClock clock;
    private final PersistedHistogram persistedHistogram;
    private final int availableProcessors;

    public LoadTestRig(final Configuration configuration)
    {
        this(configuration, createTransceiver(configuration), System.out);
    }

    public LoadTestRig(
        final Configuration configuration,
        final MessageTransceiver messageTransceiver,
        final PrintStream out)
    {
        this(
            configuration,
            messageTransceiver,
            out,
            messageTransceiver.clock,
            new PersistedHistogram(messageTransceiver.histogram),
            Runtime.getRuntime().availableProcessors());
    }

    LoadTestRig(
        final Configuration configuration,
        final MessageTransceiver messageTransceiver,
        final PrintStream out,
        final NanoClock clock,
        final PersistedHistogram persistedHistogram,
        final int availableProcessors)
    {
        this.configuration = requireNonNull(configuration);
        this.messageTransceiver = requireNonNull(messageTransceiver);
        this.out = requireNonNull(out);
        this.clock = requireNonNull(clock);
        this.persistedHistogram = requireNonNull(persistedHistogram);
        this.availableProcessors = availableProcessors;
    }

    /**
     * Run the benchmark and print histogram of the RTT values at the end.
     *
     * @throws Exception in case of any error from the {@link MessageTransceiver}
     */
    public void run() throws Exception
    {
        out.printf("%nStarting latency benchmark using the following configuration:%n%s%n", configuration);

        try
        {
            messageTransceiver.init(configuration);
            if (configuration.warmupIterations() > 0)
            {
                out.printf("%nRunning warmup for %,d iterations of %,d messages each, with %,d bytes payload and a" +
                    " burst size of %,d...%n",
                    configuration.warmupIterations(),
                    configuration.warmupMessageRate(),
                    configuration.messageLength(),
                    configuration.batchSize());
                send(configuration.warmupIterations(), configuration.warmupMessageRate());

                messageTransceiver.reset();
            }

            out.printf("%nRunning measurement for %,d iterations of %,d messages each, with %,d bytes payload and a" +
                " burst size of %,d...%n",
                configuration.iterations(),
                configuration.messageRate(),
                configuration.messageLength(),
                configuration.batchSize());
            final long sentMessages = send(configuration.iterations(), configuration.messageRate());

            out.printf("%nHistogram of RTT latencies in microseconds.%n");
            final PersistedHistogram histogram = persistedHistogram;
            histogram.outputPercentileDistribution(out, 1000.0);

            warnIfInsufficientCpu();
            warnIfTargetRateNotAchieved(sentMessages);

            histogram.saveToFile(configuration.outputDirectory(), configuration.outputFileNamePrefix());
        }
        finally
        {
            messageTransceiver.destroy();
        }
    }

    @SuppressWarnings("MethodLength")
    long send(final int iterations, final int numberOfMessages)
    {
        final MessageTransceiver messageTransceiver = this.messageTransceiver;
        final NanoClock clock = this.clock;
        final AtomicLong receivedMessages = messageTransceiver.receivedMessages;
        final int burstSize = configuration.batchSize();
        final int messageSize = configuration.messageLength();
        final IdleStrategy idleStrategy = configuration.idleStrategy();
        final long sendIntervalNs = NANOS_PER_SECOND * burstSize / numberOfMessages;
        final long totalNumberOfMessages = (long)iterations * numberOfMessages;

        final long startTimeNs = clock.nanoTime();
        final long endTimeNs = startTimeNs + iterations * NANOS_PER_SECOND;
        long sentMessages = 0;
        long timestampNs = startTimeNs;
        long nowNs = startTimeNs;
        long nextReportTimeNs = startTimeNs + NANOS_PER_SECOND;

        int batchSize = (int)min(totalNumberOfMessages, burstSize);
        while (true)
        {
            final int sent = messageTransceiver.send(batchSize, messageSize, timestampNs, CHECKSUM);

            sentMessages += sent;
            if (totalNumberOfMessages == sentMessages)
            {
                reportProgress(startTimeNs, nowNs, sentMessages);
                break;
            }

            nowNs = clock.nanoTime();
            if (sent == batchSize)
            {
                batchSize = (int)min(totalNumberOfMessages - sentMessages, burstSize);
                timestampNs += sendIntervalNs;
                if (nowNs < timestampNs && nowNs < endTimeNs)
                {
                    idleStrategy.reset();
                    long received = 0;
                    do
                    {
                        if (received < sentMessages)
                        {
                            messageTransceiver.receive();
                            final long updatedReceived = receivedMessages.get();
                            if (updatedReceived == received)
                            {
                                idleStrategy.idle();
                            }
                            else
                            {
                                received = updatedReceived;
                                idleStrategy.reset();
                            }
                        }
                        else
                        {
                            idleStrategy.idle();
                        }
                        nowNs = clock.nanoTime();
                    }
                    while (nowNs < timestampNs && nowNs < endTimeNs);
                }
            }
            else
            {
                batchSize -= sent;
                messageTransceiver.receive();
            }

            if (nowNs >= endTimeNs)
            {
                break;
            }

            if (nowNs >= nextReportTimeNs)
            {
                final int elapsedSeconds = reportProgress(startTimeNs, nowNs, sentMessages);
                nextReportTimeNs = startTimeNs + (elapsedSeconds + 1) * NANOS_PER_SECOND;
            }
        }

        idleStrategy.reset();
        long received = receivedMessages.get();
        final long deadline = clock.nanoTime() + RECEIVE_DEADLINE_NS;
        while (received < sentMessages)
        {
            messageTransceiver.receive();
            final long updatedReceived = receivedMessages.get();
            if (updatedReceived == received)
            {
                idleStrategy.idle();
                if (clock.nanoTime() >= deadline)
                {
                    out.printf("%n*** WARNING: Not all messages were received after %ds deadline!",
                        NANOSECONDS.toSeconds(RECEIVE_DEADLINE_NS));
                    break;
                }
            }
            else
            {
                received = updatedReceived;
                idleStrategy.reset();
            }
        }

        return sentMessages;
    }

    private int reportProgress(final long startTimeNs, final long nowNs, final long sentMessages)
    {
        final int elapsedSeconds = (int)round((double)(nowNs - startTimeNs) / NANOS_PER_SECOND);
        final long sendRate = 0 == elapsedSeconds ? sentMessages : sentMessages / elapsedSeconds;
        out.format("Send rate %,d msg/sec%n", sendRate);

        return elapsedSeconds;
    }

    private void warnIfInsufficientCpu()
    {
        if ((availableProcessors >>> 1) < MINIMUM_NUMBER_OF_CPU_CORES)
        {
            out.printf("%n*** WARNING: Insufficient number of CPU cores detected!" +
                "%nThe benchmarking harness requires at least %d physical CPU cores." +
                "%nThe current system reports %d logical cores which, assuming the hyper-threading is enabled, is " +
                "insufficient." +
                "%nPlease ensure that the sufficient number of physical CPU cores are available in order to obtain " +
                "reliable results.%n", MINIMUM_NUMBER_OF_CPU_CORES, availableProcessors);
        }
    }

    private void warnIfTargetRateNotAchieved(final long sentMessages)
    {
        final long expectedTotalNumberOfMessages = configuration.iterations() * (long)configuration.messageRate();
        if (sentMessages < expectedTotalNumberOfMessages)
        {
            out.printf("%n*** WARNING: Target message rate not achieved: expected to send %,d messages in " +
                "total but managed to send only %,d messages!%n", expectedTotalNumberOfMessages,
                sentMessages);
        }
    }

    private static MessageTransceiver createTransceiver(final Configuration configuration)
    {
        try
        {
            return configuration.messageTransceiverClass().getConstructor().newInstance();
        }
        catch (final ReflectiveOperationException ex)
        {
            LangUtil.rethrowUnchecked(ex);
            throw new Error();
        }
    }

    public static void main(final String[] args) throws Exception
    {
        Thread.currentThread().setName("load-test-rig");
        SystemUtil.loadPropertiesFiles(PropertyAction.REPLACE, args);

        final Configuration configuration = Configuration.fromSystemProperties();

        new LoadTestRig(configuration).run();
    }
}
