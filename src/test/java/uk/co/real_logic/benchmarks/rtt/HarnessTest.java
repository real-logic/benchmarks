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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static uk.co.real_logic.benchmarks.rtt.MessageProvider.Receiver;
import static uk.co.real_logic.benchmarks.rtt.MessageProvider.Sender;

class HarnessTest
{
    private final IdleStrategy senderIdleStrategy = mock(IdleStrategy.class);
    private final IdleStrategy receiverIdleStrategy = mock(IdleStrategy.class);
    private final MessageProvider messageProvider = mock(MessageProvider.class);
    private final NanoClock clock = mock(NanoClock.class);
    private final PrintStream out = mock(PrintStream.class);
    private final Configuration configuration = new Configuration.Builder()
        .warmUpIterations(1)
        .warmUpNumberOfMessages(1)
        .iterations(1)
        .numberOfMessages(1)
        .messageProviderClass(SampleMessageProvider.class)
        .senderIdleStrategy(senderIdleStrategy)
        .receiverIdleStrategy(receiverIdleStrategy)
        .build();

    @Test
    void constructorThrowsNullPointerExceptionIfConfigurationIsNull()
    {
        assertThrows(NullPointerException.class, () -> new Harness(null, messageProvider, clock, out));
    }

    @Test
    void constructorThrowsNullPointerExceptionIfMessageProviderIsNull()
    {
        assertThrows(NullPointerException.class, () -> new Harness(configuration, null, clock, out));
    }

    @Test
    void constructorThrowsNullPointerExceptionIfNanoClockIsNull()
    {
        assertThrows(NullPointerException.class, () -> new Harness(configuration, messageProvider, null, out));
    }

    @Test
    void constructorThrowsNullPointerExceptionIfPrintStreamIsNull()
    {
        assertThrows(NullPointerException.class, () -> new Harness(configuration, messageProvider, clock, null));
    }

    @Test
    void runThrowsNullPointerExceptionIfSenderIsNull() throws Exception
    {
        final Harness harness = new Harness(configuration, messageProvider, clock, out);

        assertThrows(NullPointerException.class, harness::run);

        final InOrder inOrder = inOrder(messageProvider);
        inOrder.verify(messageProvider).init(configuration);
        inOrder.verify(messageProvider).sender();
        inOrder.verify(messageProvider).destroy();
    }

    @Test
    void runThrowsNullPointerExceptionIfReceiverIsNull() throws Exception
    {
        final Harness harness = new Harness(configuration, messageProvider, clock, out);
        when(messageProvider.sender()).thenReturn(mock(Sender.class));

        assertThrows(NullPointerException.class, harness::run);

        final InOrder inOrder = inOrder(messageProvider);
        inOrder.verify(messageProvider).init(configuration);
        inOrder.verify(messageProvider).sender();
        inOrder.verify(messageProvider).receiver();
        inOrder.verify(messageProvider).destroy();
    }

    @Test
    void runPerformsWarmUpBeforeMeasurement() throws Exception
    {
        final long nanoTime = SECONDS.toNanos(123);
        final NanoClock clock = () -> nanoTime;
        final Harness harness = new Harness(configuration, messageProvider, clock, out);
        final Sender sender = mock(Sender.class);
        final AtomicInteger count = new AtomicInteger();
        when(sender.send(anyInt(), anyInt(), anyLong()))
            .thenAnswer((Answer<Integer>)invocation ->
            {
                final int numberOfMessages = (int)invocation.getArgument(0);
                count.getAndAdd(numberOfMessages);
                return numberOfMessages;
            });
        final Receiver receiver = mock(Receiver.class);
        when(receiver.receive()).thenAnswer((Answer<Long>)invocation ->
        {
            if (count.get() == 0)
            {
                return 0L;
            }
            count.decrementAndGet();
            return nanoTime - 100;
        });
        when(messageProvider.sender()).thenReturn(sender);
        when(messageProvider.receiver()).thenReturn(receiver);

        harness.run();

        final InOrder inOrder = inOrder(messageProvider, sender, out);
        inOrder.verify(messageProvider).init(configuration);
        inOrder.verify(messageProvider).sender();
        inOrder.verify(messageProvider).receiver();
        inOrder.verify(out)
            .printf("Running warm up for %,d iterations of %,d messages with burst size of %,d...%n",
                configuration.warmUpIterations(),
                configuration.warmUpNumberOfMessages(),
                configuration.burstSize());
        inOrder.verify(sender).send(1, configuration.messageSize(), nanoTime);
        inOrder.verify(out).format("Send rate %,d msg/sec%n", 1L);
        inOrder.verify(out)
            .printf("%nRunning measurement for %,d iterations of %,d messages with burst size of %,d...%n",
                configuration.iterations(),
                configuration.numberOfMessages(),
                configuration.burstSize());
        inOrder.verify(sender).send(1, configuration.messageSize(), nanoTime);
        inOrder.verify(out).format("Send rate %,d msg/sec%n", 1L);
        inOrder.verify(out).println("Histogram of RTT latencies in microseconds.");
        inOrder.verify(messageProvider).destroy();
    }

    @Test
    void receiveShouldKeepReceivingMessagesUpToTheSentMessagesLimit()
    {
        final Receiver receiver = mock(Receiver.class);
        when(receiver.receive()).thenReturn(1L, 0L, 2L, 3L);
        when(messageProvider.receiver()).thenReturn(receiver);
        when(clock.nanoTime()).thenReturn(10L, 20L, 30L);
        final AtomicLong sentMessages = new AtomicLong(2);
        final Histogram histogram = mock(Histogram.class);
        final Harness harness = new Harness(configuration, messageProvider, clock, out);

        harness.receive(receiver, sentMessages, histogram);

        verify(receiver, times(3)).receive();
        verify(clock, times(2)).nanoTime();
        verify(histogram).recordValue(9L);
        verify(histogram).recordValue(18L);
        verify(receiverIdleStrategy, times(2)).reset();
        verify(receiverIdleStrategy).idle();
        verifyNoMoreInteractions(receiver, histogram, clock, receiverIdleStrategy);
    }

    @Test
    void sendStopsWhenTotalNumberOfMessagesIsReached()
    {
        final Sender sender = mock(Sender.class);
        when(sender.send(anyInt(), anyInt(), anyLong()))
            .thenAnswer((Answer<Integer>)invocation -> (int)invocation.getArgument(0));
        when(messageProvider.sender()).thenReturn(sender);
        when(clock.nanoTime()).thenReturn(
            MILLISECONDS.toNanos(1000),
            MILLISECONDS.toNanos(1750),
            MILLISECONDS.toNanos(2400),
            MILLISECONDS.toNanos(2950))
            .thenThrow(new IllegalStateException("Unexpected call!"));
        final Configuration configuration = new Configuration.Builder()
            .numberOfMessages(1)
            .senderIdleStrategy(senderIdleStrategy)
            .burstSize(15)
            .messageSize(24)
            .messageProviderClass(SampleMessageProvider.class)
            .build();
        final Harness harness = new Harness(configuration, messageProvider, clock, out);

        final long messages = harness.send(2, 25, sender);

        assertEquals(50, messages);
        verify(clock, times(4)).nanoTime();
        verify(senderIdleStrategy, times(3)).reset();
        verify(sender).send(15, 24, MILLISECONDS.toNanos(1000));
        verify(sender).send(15, 24, MILLISECONDS.toNanos(1600));
        verify(sender).send(15, 24, MILLISECONDS.toNanos(2200));
        verify(sender).send(5, 24, MILLISECONDS.toNanos(2800));
        verify(out).format("Send rate %,d msg/sec%n", 30L);
        verify(out).format("Send rate %,d msg/sec%n", 25L);
        verifyNoMoreInteractions(out, clock, senderIdleStrategy, sender);
    }

    @Test
    void sendStopsIfTimeElapsesBeforeTargetNumberOfMessagesIsReached()
    {
        final Sender sender = mock(Sender.class);
        when(sender.send(anyInt(), anyInt(), anyLong())).thenReturn(15, 10, 5, 30);
        when(messageProvider.sender()).thenReturn(sender);
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
            .burstSize(30)
            .messageSize(100)
            .messageProviderClass(SampleMessageProvider.class)
            .build();
        final Harness harness = new Harness(configuration, messageProvider, clock, out);

        final long messages = harness.send(10, 100, sender);

        assertEquals(900, messages);
        verify(clock, times(5)).nanoTime();
        verify(senderIdleStrategy, times(4)).reset();
        verify(senderIdleStrategy, times(2)).idle();
        verify(sender).send(30, 100, MILLISECONDS.toNanos(500));
        verify(sender).send(15, 100, MILLISECONDS.toNanos(500));
        verify(sender).send(5, 100, MILLISECONDS.toNanos(500));
        for (int time = 800; time <= 9200; time += 300)
        {
            verify(sender).send(30, 100, MILLISECONDS.toNanos(time));
        }
        verify(out).format("Send rate %,d msg/sec%n", 5L);
        verify(out).format("Send rate %,d msg/sec%n", 70L);
        verifyNoMoreInteractions(out, clock, senderIdleStrategy, sender);
    }
}