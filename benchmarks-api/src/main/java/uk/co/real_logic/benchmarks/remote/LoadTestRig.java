/*
 * Copyright 2015-2021 Real Logic Limited.
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
import org.agrona.SystemUtil;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NanoClock;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Math.min;
import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;
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
            Runtime.getRuntime().availableProcessors()
        );
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
        messageTransceiver.init(configuration);

        try
        {
            // Warm up
            if (configuration.warmUpIterations() > 0)
            {
                out.printf("%nRunning warm up for %,d iterations of %,d messages each, with %,d bytes payload and a" +
                    " burst size of %,d...%n",
                    configuration.warmUpIterations(),
                    configuration.messageRate(),
                    configuration.messageLength(),
                    configuration.batchSize());
                send(configuration.warmUpIterations(), configuration.messageRate());

                messageTransceiver.reset();
            }

            // Measurement
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

    long send(final int iterations, final int numberOfMessages)
    {
        final MessageTransceiver messageTransceiver = this.messageTransceiver;
        final NanoClock clock = this.clock;
        final AtomicLong receivedMessages = messageTransceiver.receivedMessages;
        final int burstSize = configuration.batchSize();
        final int messageSize = configuration.messageLength();
        final IdleStrategy idleStrategy = configuration.idleStrategy();
        final long sendInterval = NANOS_PER_SECOND * burstSize / numberOfMessages;
        final long totalNumberOfMessages = (long)iterations * numberOfMessages;

        final long startTime = clock.nanoTime();
        final long endTime = startTime + iterations * NANOS_PER_SECOND;
        long sentMessages = 0;
        long timestamp = startTime;
        long now = startTime;
        long nextReportTime = startTime + NANOS_PER_SECOND;

        int batchSize = (int)min(totalNumberOfMessages, burstSize);
        while (true)
        {
            final int sent = messageTransceiver.send(batchSize, messageSize, timestamp, CHECKSUM);

            sentMessages += sent;
            if (totalNumberOfMessages == sentMessages)
            {
                reportProgress(startTime, now, sentMessages);
                break;
            }

            now = clock.nanoTime();
            if (sent == batchSize)
            {
                batchSize = (int)min(totalNumberOfMessages - sentMessages, burstSize);
                timestamp += sendInterval;
                if (now < timestamp && now < endTime)
                {
                    idleStrategy.reset();
                    do
                    {
                        final long received = receivedMessages.get();
                        messageTransceiver.receive();
                        if (received == receivedMessages.get())
                        {
                            idleStrategy.idle();
                        }
                        else
                        {
                            idleStrategy.reset();
                        }
                        now = clock.nanoTime();
                    }
                    while (now < timestamp && now < endTime);
                }
            }
            else
            {
                batchSize -= sent;
                messageTransceiver.receive();
            }

            if (now >= endTime)
            {
                break;
            }

            if (now >= nextReportTime)
            {
                final int elapsedSeconds = reportProgress(startTime, now, sentMessages);
                nextReportTime = startTime + (elapsedSeconds + 1) * NANOS_PER_SECOND;
            }
        }

        idleStrategy.reset();
        long received = receivedMessages.get();
        while (received < sentMessages)
        {
            messageTransceiver.receive();
            final long tmp = receivedMessages.get();
            if (tmp == received)
            {
                idleStrategy.idle();
            }
            else
            {
                received = tmp;
                idleStrategy.reset();
            }
        }

        return sentMessages;
    }

    private int reportProgress(final long startTime, final long now, final long sentMessages)
    {
        final int elapsedSeconds = (int)round((double)(now - startTime) / NANOS_PER_SECOND);
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
        SystemUtil.loadPropertiesFiles(args);

        final Configuration configuration = Configuration.fromSystemProperties();

        Thread.currentThread().setName("load-test-rig");
        new LoadTestRig(configuration).run();
    }
}
