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

import org.HdrHistogram.Histogram;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.NanoClock;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static java.lang.Long.MAX_VALUE;
import static java.lang.Long.MIN_VALUE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static uk.co.real_logic.benchmarks.remote.MessageTransceiverBase.CHECKSUM;

class InMemoryMessageTransceiverTest
{
    private final NanoClock clock = mock(NanoClock.class);
    private final Histogram histogram = mock(Histogram.class);
    private final InMemoryMessageTransceiver messageTransceiver = new InMemoryMessageTransceiver(clock, histogram);

    @Test
    void sendASingleMessage()
    {
        final int result = messageTransceiver.send(1, 16, 123, MIN_VALUE);

        assertEquals(1, result);
    }

    @Test
    void sendMultipleMessages()
    {
        final int result = messageTransceiver.send(4, 64, 800, -222);

        assertEquals(4, result);
    }

    @Test
    void sendReturnsZeroIfItCantFitAnEntireBatch()
    {
        messageTransceiver.send(InMemoryMessageTransceiver.SIZE, 8, 777, 100);

        final int result = messageTransceiver.send(1, 100, 555, 21);

        assertEquals(0, result);
    }

    @Test
    void receiveIsANoOpIfNothingWasWritten()
    {
        messageTransceiver.receive();

        verifyNoInteractions(clock, histogram);
    }

    @Test
    void receiveIsANoOpAfterAllMessagesWereConsumed()
    {
        when(clock.nanoTime()).thenReturn(1500L);

        messageTransceiver.send(5, 128, 1111, CHECKSUM);

        for (int i = 0; i < 5; i++)
        {
            messageTransceiver.receive();
        }
        messageTransceiver.receive();

        verify(histogram, times(5)).recordValue(389L);
        verify(clock, times(5)).nanoTime();
    }

    @Test
    void sendAndReceive()
    {
        final long[] timestamps = ThreadLocalRandom.current().longs(100_000, 1, MAX_VALUE).toArray();

        final long[] receivedTimestamps = new long[timestamps.length];
        final MutableInteger receiveIndex = new MutableInteger();
        doAnswer(invocation ->
        {
            final long timestamp = invocation.getArgument(0);
            return receivedTimestamps[receiveIndex.getAndIncrement()] = -timestamp;
        }).when(histogram).recordValue(anyLong());

        for (final long timestamp : timestamps)
        {
            while (0 == messageTransceiver.send(1, 24, timestamp, CHECKSUM))
            {
                messageTransceiver.receive();
            }
        }

        final int size = timestamps.length;
        do
        {
            messageTransceiver.receive();
        }
        while (receiveIndex.get() < size);

        assertArrayEquals(timestamps, receivedTimestamps);
    }
}
