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
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NanoClock;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.stubbing.Answer;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class LoadTestRigTest
{
    private final IdleStrategy senderIdleStrategy = mock(IdleStrategy.class);
    private final IdleStrategy receiverIdleStrategy = mock(IdleStrategy.class);
    private final NanoClock clock = mock(NanoClock.class);
    private final PrintStream out = mock(PrintStream.class);
    private final Configuration configuration = new Configuration.Builder()
        .warmUpIterations(1)
        .warmUpNumberOfMessages(1)
        .iterations(1)
        .numberOfMessages(1)
        .messageTransceiverClass(InMemoryMessageTransceiver.class)
        .sendIdleStrategy(senderIdleStrategy)
        .receiveIdleStrategy(receiverIdleStrategy)
        .build();
    private final Histogram histogram = mock(Histogram.class);
    private final MessageTransceiver messageTransceiver = mock(MessageTransceiver.class);

    @Test
    void constructorThrowsNullPointerExceptionIfConfigurationIsNull()
    {
        assertThrows(NullPointerException.class, () -> new LoadTestRig(null, MessageTransceiver.class));
    }

    @Test
    void constructorThrowsNullPointerExceptionIfMessageTransceiverClassIsNull()
    {
        assertThrows(NullPointerException.class, () -> new LoadTestRig(configuration, null));
    }

    @Test
    void constructorThrowsExceptionIfMessageTransceiverCannotBeCreated()
    {
        assertThrows(InstantiationException.class, () -> new LoadTestRig(configuration, MessageTransceiver.class));
    }

    @Test
    void runPerformsWarmUpBeforeMeasurement() throws Exception
    {
        final long nanoTime = SECONDS.toNanos(123);
        final NanoClock clock = () -> nanoTime;
        when(messageTransceiver.send(anyInt(), anyInt(), anyLong())).thenReturn(1);
        when(messageTransceiver.receive()).thenReturn(1000);
        final LoadTestRig loadTestRig =
            new LoadTestRig(configuration, clock, out, histogram, messageTransceiver);

        loadTestRig.run();

        final InOrder inOrder = inOrder(messageTransceiver, out);
        inOrder.verify(out)
            .printf("%nStarting latency benchmark using the following configuration:%n%s%n", configuration);
        inOrder.verify(messageTransceiver).init(configuration);
        inOrder.verify(out)
            .printf("%nRunning warm up for %,d iterations of %,d messages with burst size of %,d...%n",
            configuration.warmUpIterations(),
            configuration.warmUpNumberOfMessages(),
            configuration.batchSize());
        inOrder.verify(messageTransceiver).send(1, configuration.messageLength(), nanoTime);
        inOrder.verify(out).format("Send rate %,d msg/sec%n", 1L);
        inOrder.verify(out)
            .printf("%nRunning measurement for %,d iterations of %,d messages with burst size of %,d...%n",
            configuration.iterations(),
            configuration.numberOfMessages(),
            configuration.batchSize());
        inOrder.verify(messageTransceiver).send(1, configuration.messageLength(), nanoTime);
        inOrder.verify(out).format("Send rate %,d msg/sec%n", 1L);
        inOrder.verify(out).printf("%nHistogram of RTT latencies in microseconds.%n");
        inOrder.verify(messageTransceiver).destroy();
    }

    @Test
    void receiveShouldKeepReceivingMessagesUpToTheSentMessagesLimit()
    {
        when(messageTransceiver.receive()).thenReturn(1, 0, 2).thenThrow();
        final AtomicLong sentMessages = new AtomicLong(2);
        final LoadTestRig loadTestRig =
            new LoadTestRig(configuration, clock, out, histogram, messageTransceiver);

        loadTestRig.receive(sentMessages);

        verify(messageTransceiver, times(3)).receive();
        verify(receiverIdleStrategy, times(2)).reset();
        verify(receiverIdleStrategy).idle();
        verifyNoMoreInteractions(messageTransceiver, receiverIdleStrategy);
    }

    @Test
    void sendStopsWhenTotalNumberOfMessagesIsReached()
    {
        when(messageTransceiver.send(anyInt(), anyInt(), anyLong()))
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
            .build();
        final LoadTestRig loadTestRig =
            new LoadTestRig(configuration, clock, out, histogram, messageTransceiver);

        final long messages = loadTestRig.send(2, 25);

        assertEquals(50, messages);
        verify(clock, times(4)).nanoTime();
        verify(senderIdleStrategy, times(3)).reset();
        verify(messageTransceiver).send(15, 24, MILLISECONDS.toNanos(1000));
        verify(messageTransceiver).send(15, 24, MILLISECONDS.toNanos(1600));
        verify(messageTransceiver).send(15, 24, MILLISECONDS.toNanos(2200));
        verify(messageTransceiver).send(5, 24, MILLISECONDS.toNanos(2800));
        verify(out).format("Send rate %,d msg/sec%n", 30L);
        verify(out).format("Send rate %,d msg/sec%n", 25L);
        verifyNoMoreInteractions(out, clock, senderIdleStrategy, messageTransceiver);
    }

    @Test
    void sendStopsIfTimeElapsesBeforeTargetNumberOfMessagesIsReached()
    {
        when(messageTransceiver.send(anyInt(), anyInt(), anyLong())).thenReturn(15, 10, 5, 30);
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
            .build();
        final LoadTestRig loadTestRig =
            new LoadTestRig(configuration, clock, out, histogram, messageTransceiver);

        final long messages = loadTestRig.send(10, 100);

        assertEquals(900, messages);
        verify(clock, times(5)).nanoTime();
        verify(senderIdleStrategy, times(4)).reset();
        verify(senderIdleStrategy, times(3)).idle();
        verify(messageTransceiver).send(30, 100, MILLISECONDS.toNanos(500));
        verify(messageTransceiver).send(15, 100, MILLISECONDS.toNanos(500));
        verify(messageTransceiver).send(5, 100, MILLISECONDS.toNanos(500));
        for (int time = 800; time <= 9200; time += 300)
        {
            verify(messageTransceiver).send(30, 100, MILLISECONDS.toNanos(time));
        }
        verify(out).format("Send rate %,d msg/sec%n", 5L);
        verify(out).format("Send rate %,d msg/sec%n", 70L);
        verifyNoMoreInteractions(out, clock, senderIdleStrategy, messageTransceiver);
    }
}
