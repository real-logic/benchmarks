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

import org.HdrHistogram.Histogram;
import org.agrona.SystemUtil;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.SystemNanoClock;

import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toMap;

/**
 * {@code Harness} class is the core of the RTT benchmark. It is responsible for running benchmark against provided
 * {@link MessageProvider} instance using given {@link Configuration}.
 */
public final class Harness
{
    private static final long NANOS_PER_SECOND = SECONDS.toNanos(1);
    private final Configuration configuration;
    private final MessageProvider messageProvider;
    private final NanoClock clock;
    private final PrintStream out;

    public Harness(final Configuration configuration, final MessageProvider messageProvider)
    {
        this(configuration, messageProvider, SystemNanoClock.INSTANCE, System.out);
    }

    Harness(final Configuration configuration, final MessageProvider messageProvider, final NanoClock clock, final PrintStream out)
    {
        this.configuration = requireNonNull(configuration);
        this.messageProvider = requireNonNull(messageProvider);
        this.clock = requireNonNull(clock);
        this.out = requireNonNull(out);
    }

    /**
     * Run the benchmark and print histogram of the RTT values at the end.
     *
     * @throws Exception in case of any error from the {@link MessageProvider}
     */
    public void run() throws Exception
    {
        messageProvider.init(configuration);
        try
        {
            final MessageProvider.Sender sender = requireNonNull(messageProvider.sender());
            final MessageProvider.Receiver receiver = requireNonNull(messageProvider.receiver());
            final AtomicLong sentMessages = new AtomicLong();
            final Histogram histogram = new Histogram(
                max(configuration.iterations(), configuration.warmUpIterations()) * NANOS_PER_SECOND, 3);

            // Warm up
            if (configuration.warmUpIterations() > 0)
            {
                out.printf("Running warm up for %,d iterations of %,d messages with burst size of %,d...%n",
                    configuration.warmUpIterations(),
                    configuration.warmUpNumberOfMessages(),
                    configuration.burstSize());
                doRun(configuration.warmUpIterations(), configuration.warmUpNumberOfMessages(), sender, receiver,
                    sentMessages, histogram);

                histogram.reset();
                sentMessages.set(0);
            }

            // Measurement
            out.printf("%nRunning measurement for %,d iterations of %,d messages with burst size of %,d...%n",
                configuration.iterations(),
                configuration.numberOfMessages(),
                configuration.burstSize());
            doRun(configuration.iterations(), configuration.numberOfMessages(), sender, receiver, sentMessages,
                histogram);

            out.println("Histogram of RTT latencies in microseconds.");
            histogram.outputPercentileDistribution(out, 1000.0);
        }
        finally
        {
            messageProvider.destroy();
        }
    }

    private void doRun(
        final int iterations,
        final int messages,
        final MessageProvider.Sender sender,
        final MessageProvider.Receiver receiver,
        final AtomicLong sentMessages,
        final Histogram histogram)
    {
        CompletableFuture<?> receiverTask = runAsync(() -> receive(receiver, sentMessages, histogram));
        sentMessages.set(send(iterations, messages, sender));
        receiverTask.join();
    }

    void receive(final MessageProvider.Receiver receiver, final AtomicLong sentMessages, final Histogram histogram)
    {
        final NanoClock clock = this.clock;
        final IdleStrategy idleStrategy = configuration.receiverIdleStrategy();

        long sent = 0;
        long received = 0;
        while (true)
        {
            final long timestampNs = receiver.receive();
            if (0 != timestampNs)
            {
                received++;
                final long nowNs = clock.nanoTime();
                final long durationNs = nowNs - timestampNs;
                histogram.recordValue(durationNs);
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
            if (0 != sent && received == sent)
            {
                break;
            }
        }
    }

    long send(final int iterations, final int numberOfMessages, final MessageProvider.Sender sender)
    {
        final NanoClock clock = this.clock;
        final PrintStream out = this.out;
        final int burstSize = configuration.burstSize();
        final int messageSize = configuration.messageSize();
        final IdleStrategy idleStrategy = configuration.senderIdleStrategy();
        final long intervalNs = NANOS_PER_SECOND * burstSize / numberOfMessages;
        final long totalNumberOfMessages = (long)iterations * numberOfMessages;

        final long startTimeNs = clock.nanoTime();
        final long endTimeNs = startTimeNs + iterations * NANOS_PER_SECOND;
        long sentMessages = 0;
        long timestamp = startTimeNs;
        long nowNs = startTimeNs;
        long nextReportTimeNs = startTimeNs + NANOS_PER_SECOND;

        while (true)
        {
            if (nowNs >= nextReportTimeNs)
            {
                final int elapsedSeconds = reportProgress(startTimeNs, nowNs, sentMessages);
                nextReportTimeNs = startTimeNs + (elapsedSeconds + 1) * NANOS_PER_SECOND;
            }

            final int batchSize = (int)min(totalNumberOfMessages - sentMessages, burstSize);
            int sent = sender.send(batchSize, messageSize, timestamp);
            if (sent < batchSize)
            {
                idleStrategy.reset();
                while ((sent += sender.send(batchSize - sent, messageSize, timestamp)) < batchSize)
                {
                    idleStrategy.idle();
                }
            }
            sentMessages += batchSize;
            if (totalNumberOfMessages == sentMessages)
            {
                reportProgress(startTimeNs, nowNs, sentMessages);
                break;
            }

            timestamp += intervalNs;

            if (nowNs < timestamp)
            {
                idleStrategy.reset();
                while ((nowNs = clock.nanoTime()) < timestamp && nowNs < endTimeNs)
                {
                    idleStrategy.idle();
                }
                if (nowNs >= endTimeNs)
                {
                    break;
                }
            }
        }

        return sentMessages;
    }

    private int reportProgress(final long startTimeNs, final long nowNs, final long sentMessages)
    {
        int seconds = (int)round((double)(nowNs - startTimeNs) / NANOS_PER_SECOND);
        final long sendRate = 0 == seconds ? sentMessages : sentMessages / seconds;
        out.format("Send rate %,d msg/sec%n", sendRate);
        return seconds;
    }

    public static void main(String[] args) throws Exception
    {
        SystemUtil.loadPropertiesFiles(args);

        final Map<String, String> properties = System.getProperties().entrySet().stream()
            .collect(toMap(e -> (String)e.getKey(), e -> (String)e.getValue()));
        final Configuration configuration = Configuration.fromProperties(properties);

        final Class<? extends MessageProvider> messageProviderClass = configuration.messageProviderClass();
        final MessageProvider messageProvider = messageProviderClass.getConstructor().newInstance();

        new Harness(configuration, messageProvider).run();
    }
}
