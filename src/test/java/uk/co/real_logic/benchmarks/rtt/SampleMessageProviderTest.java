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

import org.junit.jupiter.api.Test;

import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static uk.co.real_logic.benchmarks.rtt.MessageProvider.Receiver;
import static uk.co.real_logic.benchmarks.rtt.MessageProvider.Sender;

class SampleMessageProviderTest
{
    private final SampleMessageProvider provider = new SampleMessageProvider();

    @Test
    void senderCreatesNewInstanceUponEachInvocation()
    {
        final Sender sender = provider.sender();
        assertNotSame(sender, provider.sender());
    }

    @Test
    void receiverCreatesNewInstanceUponEachInvocation()
    {
        final Receiver receiver = provider.receiver();
        assertNotSame(receiver, provider.receiver());
    }

    @Test
    void sendASingleMessage()
    {
        final Sender sender = provider.sender();
        final Receiver receiver = provider.receiver();

        final int result = sender.send(1, 16, 123);

        assertEquals(1, result);
        assertEquals(123, receiver.receive());
    }

    @Test
    void sendMultipleMessages()
    {
        final Sender sender = provider.sender();
        final Receiver receiver = provider.receiver();

        final int result = sender.send(4, 64, 800);

        assertEquals(4, result);
        for (int i = 0; i < result; i++)
        {
            assertEquals(800, receiver.receive());
        }
    }

    @Test
    void sendReturnsZeroIfItCantFitAnEntireBatch()
    {
        final Sender sender = provider.sender();
        final Receiver receiver = provider.receiver();
        sender.send(SampleMessageProvider.SIZE, 8, 777);

        final int result = sender.send(1, 100, 555);

        assertEquals(0, result);
        for (int i = 0; i < SampleMessageProvider.SIZE; i++)
        {
            assertEquals(777, receiver.receive());
        }
    }

    @Test
    void receiveReturnsZeroIfNothingWasWritten()
    {
        final Receiver receiver = provider.receiver();

        assertEquals(0L, receiver.receive());
    }

    @Test
    void receiveReturnsZeroAfterAllMessagesConsumed()
    {
        final Sender sender = provider.sender();
        final Receiver receiver = provider.receiver();
        sender.send(5, 128, 1111);

        for (int i = 0; i < 5; i++)
        {
            assertEquals(1111L, receiver.receive());
        }

        assertEquals(0L, receiver.receive());
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
        final Sender sender = provider.sender();
        final Receiver receiver = provider.receiver();
        final long[] timestamps = ThreadLocalRandom.current().longs(messages, 1, Long.MAX_VALUE).toArray();
        final Phaser phaser = new Phaser(3);

        final Thread senderThread = new Thread(
            () ->
            {
                phaser.arriveAndAwaitAdvance();

                for (int i = 0, size = timestamps.length; i < size; i++)
                {
                    while (0 == sender.send(1, 24, timestamps[i]))
                    {
                    }
                }
            });

        final long[] receivedTimestamps = new long[timestamps.length];
        final Thread receiverThread = new Thread(
            () ->
            {
                phaser.arriveAndAwaitAdvance();

                final int size = timestamps.length;
                int received = 0;
                while (received < size)
                {
                    final long timestamp = receiver.receive();
                    if (0 != timestamp)
                    {
                        receivedTimestamps[received++] = timestamp;
                    }
                }
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