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

import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NanoClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.co.real_logic.benchmarks.remote.LoadTestRig.CHECKSUM;
import static uk.co.real_logic.benchmarks.remote.LoadTestRig.MINIMUM_NUMBER_OF_CPU_CORES;

class LoadTestRigTest
{
    private final IdleStrategy senderIdleStrategy = mock(IdleStrategy.class);
    private final IdleStrategy receiverIdleStrategy = mock(IdleStrategy.class);
    private final NanoClock clock = mock(NanoClock.class);
    private final PrintStream out = mock(PrintStream.class);
    private final PersistedHistogram histogram = mock(PersistedHistogram.class);
    private final MessageTransceiver messageTransceiver = mock(MessageTransceiver.class);
    private Configuration configuration;

    @BeforeEach
    void before(final @TempDir Path tempDir)
    {
        configuration = new Configuration.Builder()
            .warmUpIterations(0)
            .iterations(1)
            .numberOfMessages(1)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .sendIdleStrategy(senderIdleStrategy)
            .receiveIdleStrategy(receiverIdleStrategy)
            .outputDirectory(tempDir)
            .outputFileNamePrefix("test")
            .build();
    }

    @Test
    void constructorThrowsNullPointerExceptionIfConfigurationIsNull()
    {
        assertThrows(NullPointerException.class, () -> new LoadTestRig(null));
    }

    @Test
    void runPerformsWarmUpBeforeMeasurement() throws Exception
    {
        final long nanoTime = SECONDS.toNanos(123);
        final NanoClock clock = () -> nanoTime;

        when(messageTransceiver.send(anyInt(), anyInt(), anyLong(), anyLong())).thenReturn(1);

        configuration = new Configuration.Builder()
            .warmUpIterations(1)
            .iterations(1)
            .numberOfMessages(1)
            .messageTransceiverClass(configuration.messageTransceiverClass())
            .sendIdleStrategy(senderIdleStrategy)
            .receiveIdleStrategy(receiverIdleStrategy)
            .outputDirectory(configuration.outputDirectory())
            .outputFileNamePrefix("test")
            .build();

        final LoadTestRig loadTestRig = new LoadTestRig(
            configuration,
            clock,
            out,
            histogram,
            MINIMUM_NUMBER_OF_CPU_CORES * 2,
            messageRecorder ->
            {
                doAnswer(invocation ->
                {
                    messageRecorder.record(1, CHECKSUM);
                    messageRecorder.record(1, CHECKSUM);
                    messageRecorder.record(1, CHECKSUM);
                    return null;
                }).when(messageTransceiver).receive();

                return messageTransceiver;
            });

        loadTestRig.run();

        final InOrder inOrder = inOrder(messageTransceiver, out, histogram);
        inOrder.verify(out)
            .printf("%nStarting latency benchmark using the following configuration:%n%s%n", configuration);
        inOrder.verify(messageTransceiver).init(configuration);
        inOrder.verify(out).printf("%nRunning warm up for %,d iterations of %,d messages each, with %,d bytes payload" +
            " and a burst size of %,d...%n",
            configuration.warmUpIterations(),
            configuration.numberOfMessages(),
            configuration.messageLength(),
            configuration.batchSize());
        inOrder.verify(messageTransceiver).send(1, configuration.messageLength(), nanoTime, CHECKSUM);
        inOrder.verify(out).format("Send rate %,d msg/sec%n", 1L);
        inOrder.verify(histogram).reset();
        inOrder.verify(out).printf("%nRunning measurement for %,d iterations of %,d messages each, with %,d bytes" +
            " payload and a burst size of %,d...%n",
            configuration.iterations(),
            configuration.numberOfMessages(),
            configuration.messageLength(),
            configuration.batchSize());
        inOrder.verify(messageTransceiver).send(1, configuration.messageLength(), nanoTime, CHECKSUM);
        inOrder.verify(out).format("Send rate %,d msg/sec%n", 1L);
        inOrder.verify(out).printf("%nHistogram of RTT latencies in microseconds.%n");
        inOrder.verify(histogram).outputPercentileDistribution(out, 1000.0);
        inOrder.verify(histogram).saveToFile(configuration.outputDirectory(), configuration.outputFileNamePrefix());
        inOrder.verify(messageTransceiver).destroy();
    }

    @Test
    void runWarnsAboutInsufficientCpu() throws Exception
    {
        final long nanoTime = SECONDS.toNanos(123);
        final NanoClock clock = () -> nanoTime;

        when(messageTransceiver.send(anyInt(), anyInt(), anyLong(), anyLong())).thenReturn(1);

        final LoadTestRig loadTestRig = new LoadTestRig(
            configuration,
            clock,
            out,
            histogram,
            MINIMUM_NUMBER_OF_CPU_CORES + 1,
            messageRecorder ->
            {
                doAnswer(invocation ->
                {
                    messageRecorder.record(1, CHECKSUM);
                    return null;
                }).when(messageTransceiver).receive();

                return messageTransceiver;
            });

        loadTestRig.run();


        verify(out).printf("%n*** WARNING: Insufficient number of CPU cores detected!" +
            "%nThe benchmarking harness requires at least %d physical CPU cores." +
            "%nThe current system reports %d logical cores which, assuming the hyper-threading is enabled, is " +
            "insufficient." +
            "%nPlease ensure that the sufficient number of physical CPU cores are available in order to obtain " +
            "reliable results.%n",
            MINIMUM_NUMBER_OF_CPU_CORES, MINIMUM_NUMBER_OF_CPU_CORES + 1);
    }

    @Test
    void runWarnsAboutMissedTargetRate() throws Exception
    {
        when(clock.nanoTime()).thenReturn(1L, 9_000_000_000L);

        when(messageTransceiver.send(anyInt(), anyInt(), anyLong(), anyLong()))
            .thenAnswer((Answer<Integer>)invocation -> invocation.getArgument(0));

        configuration = new Configuration.Builder()
            .warmUpIterations(0)
            .iterations(3)
            .numberOfMessages(5)
            .batchSize(2)
            .messageTransceiverClass(configuration.messageTransceiverClass())
            .sendIdleStrategy(senderIdleStrategy)
            .receiveIdleStrategy(receiverIdleStrategy)
            .outputDirectory(configuration.outputDirectory())
            .outputFileNamePrefix("test")
            .build();

        final LoadTestRig loadTestRig = new LoadTestRig(
            configuration,
            clock,
            out,
            histogram,
            MINIMUM_NUMBER_OF_CPU_CORES * 2,
            messageRecorder ->
            {
                doAnswer(invocation ->
                {
                    messageRecorder.record(1, CHECKSUM);
                    return null;
                }).when(messageTransceiver).receive();

                return messageTransceiver;
            });

        loadTestRig.run();

        verify(out).printf("%n*** WARNING: Target message rate not achieved: expected to send %,d messages in " +
            "total but managed to send only %,d messages!%n", 15L, 2L);
    }

    @Test
    void receiveShouldKeepReceivingMessagesUpToTheSentMessagesLimit()
    {
        configuration = new Configuration.Builder()
            .warmUpIterations(0)
            .iterations(1)
            .numberOfMessages(3)
            .batchSize(200)
            .messageTransceiverClass(configuration.messageTransceiverClass())
            .sendIdleStrategy(senderIdleStrategy)
            .receiveIdleStrategy(receiverIdleStrategy)
            .outputDirectory(configuration.outputDirectory())
            .outputFileNamePrefix("test")
            .build();

        when(messageTransceiver.send(anyInt(), anyInt(), anyLong(), anyLong()))
            .thenAnswer((Answer<Integer>)invocation -> invocation.getArgument(0));

        final LoadTestRig loadTestRig = new LoadTestRig(
            configuration,
            clock,
            out,
            histogram,
            MINIMUM_NUMBER_OF_CPU_CORES * 2,
            messageRecorder ->
            {
                doAnswer(new Answer<Void>()
                {
                    private int invocationCount = 0;

                    public Void answer(final InvocationOnMock invocation)
                    {
                        if (++invocationCount == 2)
                        {
                            return null;
                        }

                        for (int i = 0; i < invocationCount; i++)
                        {
                            messageRecorder.record(i, CHECKSUM);
                        }

                        return null;
                    }
                }).when(messageTransceiver).receive();

                return messageTransceiver;
            });

        loadTestRig.send(1, 3);

        loadTestRig.receive();

        verify(messageTransceiver).send(anyInt(), anyInt(), anyLong(), anyLong());
        verify(messageTransceiver, times(3)).receive();
        verify(receiverIdleStrategy, times(2)).reset();
        verify(receiverIdleStrategy).idle();
        verifyNoMoreInteractions(messageTransceiver, receiverIdleStrategy);
    }

    @Test
    void sendStopsWhenTotalNumberOfMessagesIsReached(final @TempDir Path tempDir)
    {
        when(messageTransceiver.send(anyInt(), anyInt(), anyLong(), anyLong()))
            .thenAnswer((Answer<Integer>)invocation -> (int)invocation.getArgument(0));
        when(clock.nanoTime()).thenReturn(
            MILLISECONDS.toNanos(1000),
            MILLISECONDS.toNanos(1750),
            MILLISECONDS.toNanos(2400),
            MILLISECONDS.toNanos(2950))
            .thenThrow(new IllegalStateException("Unexpected call!"));

        final Configuration configuration = new Configuration.Builder()
            .numberOfMessages(1)
            .sendIdleStrategy(senderIdleStrategy)
            .batchSize(15)
            .messageLength(24)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .outputDirectory(tempDir)
            .outputFileNamePrefix("test")
            .build();

        final LoadTestRig loadTestRig = new LoadTestRig(
            configuration,
            clock,
            out,
            histogram,
            MINIMUM_NUMBER_OF_CPU_CORES * 2,
            messageRecorder -> messageTransceiver);

        final long messages = loadTestRig.send(2, 25);

        assertEquals(50, messages);
        verify(clock, times(4)).nanoTime();
        verify(messageTransceiver).send(15, 24, MILLISECONDS.toNanos(1000), CHECKSUM);
        verify(messageTransceiver).send(15, 24, MILLISECONDS.toNanos(1600), CHECKSUM);
        verify(messageTransceiver).send(15, 24, MILLISECONDS.toNanos(2200), CHECKSUM);
        verify(messageTransceiver).send(5, 24, MILLISECONDS.toNanos(2800), CHECKSUM);
        verify(out).format("Send rate %,d msg/sec%n", 30L);
        verify(out).format("Send rate %,d msg/sec%n", 25L);
        verifyNoMoreInteractions(out, clock, senderIdleStrategy, messageTransceiver);
    }

    @Test
    void sendStopsIfTimeElapsesBeforeTargetNumberOfMessagesIsReached(final @TempDir Path tempDir)
    {
        when(messageTransceiver.send(anyInt(), anyInt(), anyLong(), anyLong())).thenReturn(15, 10, 5, 30);
        when(clock.nanoTime()).thenReturn(
            MILLISECONDS.toNanos(500),
            MILLISECONDS.toNanos(777),
            MILLISECONDS.toNanos(6750),
            MILLISECONDS.toNanos(9200),
            MILLISECONDS.toNanos(12000)
        ).thenThrow(new IllegalStateException("Unexpected call!"));

        final Configuration configuration = new Configuration.Builder()
            .numberOfMessages(1)
            .sendIdleStrategy(senderIdleStrategy)
            .batchSize(30)
            .messageLength(100)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .outputDirectory(tempDir)
            .outputFileNamePrefix("test")
            .build();

        final LoadTestRig loadTestRig = new LoadTestRig(
            configuration,
            clock,
            out,
            histogram,
            MINIMUM_NUMBER_OF_CPU_CORES * 2,
            messageRecorder -> messageTransceiver);

        final long messages = loadTestRig.send(10, 100);

        assertEquals(90, messages);
        verify(clock, times(5)).nanoTime();
        verify(senderIdleStrategy, times(2)).reset();
        verify(senderIdleStrategy, times(3)).idle();
        verify(messageTransceiver).send(30, 100, MILLISECONDS.toNanos(500), CHECKSUM);
        verify(messageTransceiver).send(15, 100, MILLISECONDS.toNanos(500), CHECKSUM);
        verify(messageTransceiver).send(5, 100, MILLISECONDS.toNanos(500), CHECKSUM);
        verify(messageTransceiver).send(30, 100, MILLISECONDS.toNanos(800), CHECKSUM);
        verify(messageTransceiver).send(30, 100, MILLISECONDS.toNanos(1100), CHECKSUM);
        verify(out).format("Send rate %,d msg/sec%n", 5L);
        verify(out).format("Send rate %,d msg/sec%n", 6L);
        verifyNoMoreInteractions(out, clock, senderIdleStrategy, messageTransceiver);
    }

    @Timeout(10)
    @Test
    void endToEndTest(final @TempDir Path tempDir) throws Exception
    {
        final Configuration configuration = new Configuration.Builder()
            .iterations(3)
            .numberOfMessages(10_000)
            .batchSize(5)
            .warmUpIterations(0)
            .messageTransceiverClass(InMemoryMessageTransceiver.class)
            .outputDirectory(tempDir)
            .outputFileNamePrefix("test")
            .build();
        final LoadTestRig testRig = new LoadTestRig(configuration);

        testRig.run();

        final File[] files = tempDir.toFile().listFiles();
        assertNotNull(files);
        assertEquals(1, files.length);
    }
}
