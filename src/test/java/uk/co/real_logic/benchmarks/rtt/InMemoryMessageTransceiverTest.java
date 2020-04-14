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

import org.agrona.collections.MutableInteger;
import org.agrona.hints.ThreadHints;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class InMemoryMessageTransceiverTest
{
    private final MessageRecorder messageRecorder = mock(MessageRecorder.class);
    private final InMemoryMessageTransceiver messageTransceiver = new InMemoryMessageTransceiver(messageRecorder);

    @Test
    void sendASingleMessage()
    {
        final int result = messageTransceiver.send(1, 16, 123);

        assertEquals(1, result);
    }

    @Test
    void sendMultipleMessages()
    {
        final int result = messageTransceiver.send(4, 64, 800);

        assertEquals(4, result);
    }

    @Test
    void sendReturnsZeroIfItCantFitAnEntireBatch()
    {
        messageTransceiver.send(InMemoryMessageTransceiver.SIZE, 8, 777);

        final int result = messageTransceiver.send(1, 100, 555);

        assertEquals(0, result);
    }

    @Test
    void receiveIsANoOpIfNothingWasWritten()
    {
        messageTransceiver.receive();

        verifyNoInteractions(messageRecorder);
    }

    @Test
    void receiveIsANoOpAfterAllMessagesWereConsumed()
    {
        messageTransceiver.send(5, 128, 1111);

        for (int i = 0; i < 5; i++)
        {
            messageTransceiver.receive();
        }
        messageTransceiver.receive();

        verify(messageRecorder, times(5)).record(1111);
    }

    @Test
    void concurrentSendAndReceive() throws InterruptedException
    {
        for (int i = 0; i < 10; i++)
        {
            testConcurrentSendAndReceive(100_000);
        }
    }

    private void testConcurrentSendAndReceive(final int messages) throws InterruptedException
    {
        final long[] timestamps = ThreadLocalRandom.current().longs(messages, 1, Long.MAX_VALUE).toArray();
        final Phaser phaser = new Phaser(3);

        final long[] receivedTimestamps = new long[timestamps.length];
        final MutableInteger receiveIndex = new MutableInteger();
        final MessageTransceiver messageTransceiver = new InMemoryMessageTransceiver(
            timestamp -> receivedTimestamps[receiveIndex.getAndIncrement()] = timestamp
        );

        final Thread senderThread = new Thread(
            () ->
            {
                phaser.arriveAndAwaitAdvance();

                for (final long timestamp : timestamps)
                {
                    while (0 == messageTransceiver.send(1, 24, timestamp))
                    {
                        ThreadHints.onSpinWait();
                    }
                }
            });

        final Thread receiverThread = new Thread(
            () ->
            {
                phaser.arriveAndAwaitAdvance();

                final int size = timestamps.length;
                do
                {
                    messageTransceiver.receive();
                }
                while (receiveIndex.get() < size);
            }
        );

        senderThread.start();
        receiverThread.start();

        phaser.arriveAndDeregister();

        senderThread.join();
        receiverThread.join();

        assertArrayEquals(timestamps, receivedTimestamps);
    }
}
