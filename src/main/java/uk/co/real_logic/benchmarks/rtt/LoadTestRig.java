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
package uk.co.real_logic.benchmarks.rtt;

import org.agrona.LangUtil;
import org.agrona.SystemUtil;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.SystemNanoClock;

import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

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
    private final RttHistogram histogram;
    private final AtomicLong sentMessages;
    private final AtomicLong receivedMessages;
    private final int availableProcessors;

    public LoadTestRig(final Configuration configuration)
    {
        this.configuration = requireNonNull(configuration);
        final Class<? extends MessageTransceiver> messageTransceiverClass =
            requireNonNull(configuration.messageTransceiverClass());
        clock = SystemNanoClock.INSTANCE;
        out = System.out;
        availableProcessors = Runtime.getRuntime().availableProcessors();
        histogram = new RttHistogram();
        sentMessages = new AtomicLong();
        receivedMessages = new AtomicLong();
        try
        {
            messageTransceiver = messageTransceiverClass.getConstructor(MessageRecorder.class)
                .newInstance((MessageRecorder)(timestamp, checksum) ->
                {
                    if (CHECKSUM != checksum)
                    {
                        throw new IllegalStateException("Invalid message checksum!");
                    }
                    histogram.recordValue(clock.nanoTime() - timestamp);
                    receivedMessages.getAndIncrement();
                });
        }
        catch (final ReflectiveOperationException ex)
        {
            LangUtil.rethrowUnchecked(ex);
            throw new Error();
        }
    }

    LoadTestRig(
        final Configuration configuration,
        final NanoClock clock,
        final PrintStream out,
        final RttHistogram histogram,
        final MessageTransceiver messageTransceiver,
        final AtomicLong sentMessages,
        final AtomicLong receivedMessages,
        final int availableProcessors)
    {
        this.configuration = configuration;
        this.clock = clock;
        this.out = out;
        this.histogram = histogram;
        this.messageTransceiver = messageTransceiver;
        this.sentMessages = sentMessages;
        this.receivedMessages = receivedMessages;
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
                    configuration.warmUpNumberOfMessages(),
                    configuration.messageLength(),
                    configuration.batchSize());
                doRun(configuration.warmUpIterations(), configuration.warmUpNumberOfMessages());

                histogram.reset();
                sentMessages.set(0);
                receivedMessages.set(0);
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
        sentMessages.set(send(iterations, messages));
        receiverTask.join();
    }

    void receive()
    {
        final MessageTransceiver messageTransceiver = this.messageTransceiver;
        final IdleStrategy idleStrategy = configuration.receiveIdleStrategy();
        final AtomicLong sentMessages = this.sentMessages;
        final AtomicLong receivedMessages = this.receivedMessages;

        long sent = 0;
        long received = 0;
        while (true)
        {
            messageTransceiver.receive();

            final long receivedMessagesCount = receivedMessages.get();
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
                sent = sentMessages.get();
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
        final IdleStrategy idleStrategy = configuration.sendIdleStrategy();
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
        if (sentMessages.get() < expectedTotalNumberOfMessages)
        {
            out.printf("%n*** WARNING: Target message rate not achieved: expected to send %,d messages in " +
                "total but managed to send only %,d messages!%n", expectedTotalNumberOfMessages,
                sentMessages.get());
        }
    }

    public static void main(final String[] args) throws Exception
    {
        SystemUtil.loadPropertiesFiles(args);

        final Configuration configuration = Configuration.fromSystemProperties();
        new LoadTestRig(configuration).run();
    }
}
