/*
 * Copyright 2015-2020 Real Logic Limited.
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
import org.agrona.concurrent.SystemNanoClock;

import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

import static java.lang.Math.min;
import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * {@code LoadTestRig} class is the core of the RTT benchmark. It is responsible for running benchmark against provided
 * {@link MessageTransceiver} instance using given {@link Configuration}.
 */
public final class LoadTestRig
{
    static final int MINIMUM_NUMBER_OF_CPU_CORES = 6;
    static final long CHECKSUM = ThreadLocalRandom.current().nextLong();

    private static final long NANOS_PER_SECOND = SECONDS.toNanos(1);
    private final Configuration configuration;
    private final MessageTransceiver messageTransceiver;
    private final NanoClock clock;
    private final PrintStream out;
    private final PersistedHistogram histogram;
    private final int availableProcessors;
    private long sentMessages;
    private long receivedMessages;

    public LoadTestRig(final Configuration configuration)
    {
        this(
            configuration,
            SystemNanoClock.INSTANCE,
            System.out, new PersistedHistogram(),
            Runtime.getRuntime().availableProcessors(),
            messageRecorder ->
            {
                final Class<? extends MessageTransceiver> messageTransceiverClass =
                    requireNonNull(configuration.messageTransceiverClass());
                try
                {
                    return messageTransceiverClass.getConstructor(MessageRecorder.class).newInstance(messageRecorder);
                }
                catch (final ReflectiveOperationException ex)
                {
                    LangUtil.rethrowUnchecked(ex);
                    throw new Error();
                }
            });
    }

    LoadTestRig(
        final Configuration configuration,
        final NanoClock clock,
        final PrintStream out,
        final PersistedHistogram histogram,
        final int availableProcessors,
        final Function<MessageRecorder, MessageTransceiver> messageTransceiverSupplier)
    {
        this.configuration = requireNonNull(configuration);
        this.clock = requireNonNull(clock);
        this.out = requireNonNull(out);
        this.histogram = requireNonNull(histogram);
        this.messageTransceiver = messageTransceiverSupplier.apply(
            (timestamp, checksum) ->
            {
                if (CHECKSUM != checksum)
                {
                    throw new IllegalStateException("Invalid checksum: expected=" + CHECKSUM + ", actual=" + checksum);
                }
                histogram.recordValue(clock.nanoTime() - timestamp);
                receivedMessages++;
            });
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
                    configuration.numberOfMessages(),
                    configuration.messageLength(),
                    configuration.batchSize());
                doRun(configuration.warmUpIterations(), configuration.numberOfMessages());

                histogram.reset();
                sentMessages = 0;
                receivedMessages = 0;
            }

            // Measurement
            out.printf("%nRunning measurement for %,d iterations of %,d messages each, with %,d bytes payload and a" +
                " burst size of %,d...%n",
                configuration.iterations(),
                configuration.numberOfMessages(),
                configuration.messageLength(),
                configuration.batchSize());
            doRun(configuration.iterations(), configuration.numberOfMessages());

            out.printf("%nHistogram of RTT latencies in microseconds.%n");
            histogram.outputPercentileDistribution(out, 1000.0);

            warnIfInsufficientCpu();
            warnIfTargetRateNotAchieved();

            histogram.saveToFile(configuration.outputDirectory(), configuration.outputFileNamePrefix());
        }
        finally
        {
            messageTransceiver.destroy();
        }
    }

    private void doRun(final int iterations, final int messages)
    {
        final CompletableFuture<?> receiverTask = runAsync(this::receive);
        send(iterations, messages);
        receiverTask.join();
    }

    void receive()
    {
        final MessageTransceiver messageTransceiver = this.messageTransceiver;
        final IdleStrategy idleStrategy = configuration.idleStrategy();

        long sent = 0;
        long received = 0;
        while (true)
        {
            messageTransceiver.receive();

            final long receivedMessagesCount = receivedMessages;
            if (receivedMessagesCount != received)
            {
                received = receivedMessagesCount;
                idleStrategy.reset();
            }
            else
            {
                idleStrategy.idle();
            }

            if (0 == sent)
            {
                sent = sentMessages;
            }

            if (0 != sent && received >= sent)
            {
                break;
            }
        }
    }

    long send(final int iterations, final int numberOfMessages)
    {
        final MessageTransceiver messageTransceiver = this.messageTransceiver;
        final NanoClock clock = this.clock;
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

        while (true)
        {
            final int batchSize = (int)min(totalNumberOfMessages - sentMessages, burstSize);
            int sent = messageTransceiver.send(batchSize, messageSize, timestamp, CHECKSUM);
            if (sent < batchSize)
            {
                idleStrategy.reset();
                do
                {
                    idleStrategy.idle();
                    sent += messageTransceiver.send(batchSize - sent, messageSize, timestamp, CHECKSUM);
                }
                while (sent < batchSize);
            }

            sentMessages += batchSize;
            if (totalNumberOfMessages == sentMessages)
            {
                reportProgress(startTime, now, sentMessages);
                break;
            }

            timestamp += sendInterval;
            now = clock.nanoTime();

            if (now < timestamp && now < endTime)
            {
                idleStrategy.reset();
                do
                {
                    idleStrategy.idle();
                    now = clock.nanoTime();
                }
                while (now < timestamp && now < endTime);
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

        this.sentMessages = sentMessages;

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

    private void warnIfTargetRateNotAchieved()
    {
        final long expectedTotalNumberOfMessages = configuration.iterations() * (long)configuration.numberOfMessages();
        if (sentMessages < expectedTotalNumberOfMessages)
        {
            out.printf("%n*** WARNING: Target message rate not achieved: expected to send %,d messages in " +
                "total but managed to send only %,d messages!%n", expectedTotalNumberOfMessages,
                sentMessages);
        }
    }

    public static void main(final String[] args) throws Exception
    {
        SystemUtil.loadPropertiesFiles(args);

        final Configuration configuration = Configuration.fromSystemProperties();
        new LoadTestRig(configuration).run();
    }
}
