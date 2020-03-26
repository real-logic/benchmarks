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
        .messagePumpClass(SampleMessagePump.class)
        .senderIdleStrategy(senderIdleStrategy)
        .receiverIdleStrategy(receiverIdleStrategy)
        .build();
    private Histogram histogram = mock(Histogram.class);
    private MessagePump messagePump = mock(MessagePump.class);

    @Test
    void constructorThrowsNullPointerExceptionIfConfigurationIsNull()
    {
        assertThrows(NullPointerException.class, () -> new LoadTestRig(null, MessagePump.class));
    }

    @Test
    void constructorThrowsNullPointerExceptionIfMessagePumpClassIsNull()
    {
        assertThrows(NullPointerException.class, () -> new LoadTestRig(configuration, null));
    }

    @Test
    void constructorThrowsExceptionIfMessagePumpCannotBeCreated()
    {
        assertThrows(InstantiationException.class, () -> new LoadTestRig(configuration, MessagePump.class));
    }

    @Test
    void runPerformsWarmUpBeforeMeasurement() throws Exception
    {
        final long nanoTime = SECONDS.toNanos(123);
        final NanoClock clock = () -> nanoTime;
        when(messagePump.send(anyInt(), anyInt(), anyLong())).thenReturn(1);
        when(messagePump.receive()).thenReturn(1000);
        final LoadTestRig loadTestRig =
            new LoadTestRig(configuration, clock, out, histogram, messagePump);

        loadTestRig.run();

        final InOrder inOrder = inOrder(messagePump, out);
        inOrder.verify(out)
            .printf("Starting latency benchmark using the following configuration:%n%s%n", configuration);
        inOrder.verify(messagePump).init(configuration);
        inOrder.verify(out)
            .printf("%nRunning warm up for %,d iterations of %,d messages with burst size of %,d...%n",
            configuration.warmUpIterations(),
            configuration.warmUpNumberOfMessages(),
            configuration.batchSize());
        inOrder.verify(messagePump).send(1, configuration.messageLength(), nanoTime);
        inOrder.verify(out).format("Send rate %,d msg/sec%n", 1L);
        inOrder.verify(out)
            .printf("%nRunning measurement for %,d iterations of %,d messages with burst size of %,d...%n",
            configuration.iterations(),
            configuration.numberOfMessages(),
            configuration.batchSize());
        inOrder.verify(messagePump).send(1, configuration.messageLength(), nanoTime);
        inOrder.verify(out).format("Send rate %,d msg/sec%n", 1L);
        inOrder.verify(out).printf("%nHistogram of RTT latencies in microseconds.%n");
        inOrder.verify(messagePump).destroy();
    }

    @Test
    void receiveShouldKeepReceivingMessagesUpToTheSentMessagesLimit()
    {
        when(messagePump.receive()).thenReturn(1, 0, 2).thenThrow();
        final AtomicLong sentMessages = new AtomicLong(2);
        final LoadTestRig loadTestRig =
            new LoadTestRig(configuration, clock, out, histogram, messagePump);

        loadTestRig.receive(sentMessages);

        verify(messagePump, times(3)).receive();
        verify(receiverIdleStrategy, times(2)).reset();
        verify(receiverIdleStrategy).idle();
        verifyNoMoreInteractions(messagePump, receiverIdleStrategy);
    }

    @Test
    void sendStopsWhenTotalNumberOfMessagesIsReached()
    {
        when(messagePump.send(anyInt(), anyInt(), anyLong()))
            .thenAnswer((Answer<Integer>)invocation -> (int)invocation.getArgument(0));
        when(clock.nanoTime()).thenReturn(
            MILLISECONDS.toNanos(1000),
            MILLISECONDS.toNanos(1750),
            MILLISECONDS.toNanos(2400),
            MILLISECONDS.toNanos(2950))
            .thenThrow(new IllegalStateException("Unexpected call!"));
        final Configuration configuration = new Configuration.Builder()
            .numberOfMessages(1)
            .senderIdleStrategy(senderIdleStrategy)
            .batchSize(15)
            .messageLength(24)
            .messagePumpClass(SampleMessagePump.class)
            .build();
        final LoadTestRig loadTestRig =
            new LoadTestRig(configuration, clock, out, histogram, messagePump);

        final long messages = loadTestRig.send(2, 25);

        assertEquals(50, messages);
        verify(clock, times(4)).nanoTime();
        verify(senderIdleStrategy, times(3)).reset();
        verify(messagePump).send(15, 24, MILLISECONDS.toNanos(1000));
        verify(messagePump).send(15, 24, MILLISECONDS.toNanos(1600));
        verify(messagePump).send(15, 24, MILLISECONDS.toNanos(2200));
        verify(messagePump).send(5, 24, MILLISECONDS.toNanos(2800));
        verify(out).format("Send rate %,d msg/sec%n", 30L);
        verify(out).format("Send rate %,d msg/sec%n", 25L);
        verifyNoMoreInteractions(out, clock, senderIdleStrategy, messagePump);
    }

    @Test
    void sendStopsIfTimeElapsesBeforeTargetNumberOfMessagesIsReached()
    {
        when(messagePump.send(anyInt(), anyInt(), anyLong())).thenReturn(15, 10, 5, 30);
        when(clock.nanoTime()).thenReturn(
            MILLISECONDS.toNanos(500),
            MILLISECONDS.toNanos(777),
            MILLISECONDS.toNanos(6750),
            MILLISECONDS.toNanos(9200),
            MILLISECONDS.toNanos(12000)
        ).thenThrow(new IllegalStateException("Unexpected call!"));
        final Configuration configuration = new Configuration.Builder()
            .numberOfMessages(1)
            .senderIdleStrategy(senderIdleStrategy)
            .batchSize(30)
            .messageLength(100)
            .messagePumpClass(SampleMessagePump.class)
            .build();
        final LoadTestRig loadTestRig =
            new LoadTestRig(configuration, clock, out, histogram, messagePump);

        final long messages = loadTestRig.send(10, 100);

        assertEquals(900, messages);
        verify(clock, times(5)).nanoTime();
        verify(senderIdleStrategy, times(4)).reset();
        verify(senderIdleStrategy, times(3)).idle();
        verify(messagePump).send(30, 100, MILLISECONDS.toNanos(500));
        verify(messagePump).send(15, 100, MILLISECONDS.toNanos(500));
        verify(messagePump).send(5, 100, MILLISECONDS.toNanos(500));
        for (int time = 800; time <= 9200; time += 300)
        {
            verify(messagePump).send(30, 100, MILLISECONDS.toNanos(time));
        }
        verify(out).format("Send rate %,d msg/sec%n", 5L);
        verify(out).format("Send rate %,d msg/sec%n", 70L);
        verifyNoMoreInteractions(out, clock, senderIdleStrategy, messagePump);
    }
}