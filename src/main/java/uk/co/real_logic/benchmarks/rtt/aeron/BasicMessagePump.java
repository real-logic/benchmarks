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
package uk.co.real_logic.benchmarks.rtt.aeron;

import io.aeron.*;
import io.aeron.exceptions.AeronException;
import org.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.benchmarks.rtt.Configuration;
import uk.co.real_logic.benchmarks.rtt.MessagePump;
import uk.co.real_logic.benchmarks.rtt.MessageRecorder;

import static io.aeron.Publication.*;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronLauncher.*;

public final class BasicMessagePump extends MessagePump
{
    private final AeronLauncher launcher;
    private final int frameCountLimit;
    private ExclusivePublication publication;
    private UnsafeBuffer offerBuffer;

    private Subscription subscription;
    private Image image;
    private int messagesReceived = 0;
    private final FragmentAssembler dataHandler = new FragmentAssembler(
        (buffer, offset, length, header) ->
        {
            onMessageReceived(buffer.getLong(offset, LITTLE_ENDIAN));
            messagesReceived++;
        });

    public BasicMessagePump(final MessageRecorder messageRecorder)
    {
        this(new AeronLauncher(), messageRecorder);
    }

    BasicMessagePump(final AeronLauncher launcher, final MessageRecorder messageRecorder)
    {
        super(messageRecorder);
        this.launcher = launcher;
        frameCountLimit = frameCountLimit();
    }

    public void init(final Configuration configuration) throws Exception
    {
        final Aeron aeron = launcher.aeron();

        final ExclusivePublication publication = aeron.addExclusivePublication(senderChannel(), senderStreamId());
        this.publication = publication;

        final Subscription subscription = aeron.addSubscription(receiverChannel(), receiverStreamId());
        this.subscription = subscription;

        while (!subscription.isConnected() || !publication.isConnected())
        {
            Thread.yield();
        }

        offerBuffer = new UnsafeBuffer(
            allocateDirectAligned(configuration.messageLength(), CACHE_LINE_LENGTH));

        image = subscription.imageAtIndex(0);
    }

    public void destroy() throws Exception
    {
        closeAll(publication, subscription, launcher);
    }

    public int send(final int numberOfMessages, final int length, final long timestamp)
    {
        int count = 0;
        final UnsafeBuffer offerBuffer = this.offerBuffer;
        final ExclusivePublication publication = this.publication;
        for (int i = 0; i < numberOfMessages; i++)
        {
            offerBuffer.putLong(0, timestamp, LITTLE_ENDIAN);
            final long result = publication.offer(offerBuffer, 0, length);
            if (result < 0)
            {
                checkResult(result);
                break;
            }
            count++;
        }
        return count;
    }

    public int receive()
    {
        messagesReceived = 0;
        final Image image = this.image;
        final int fragments = image.poll(dataHandler, frameCountLimit);
        if (0 == fragments && image.isClosed())
        {
            throw new IllegalStateException("image closed unexpectedly");
        }
        return messagesReceived;
    }

    private static void checkResult(final long result)
    {
        if (result == CLOSED ||
            result == NOT_CONNECTED ||
            result == MAX_POSITION_EXCEEDED)
        {
            throw new AeronException("Publication error: " + result);
        }
    }
}
