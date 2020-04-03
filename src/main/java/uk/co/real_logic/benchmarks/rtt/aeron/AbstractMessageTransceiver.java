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

import io.aeron.ExclusivePublication;
import io.aeron.FragmentAssembler;
import io.aeron.Image;
import io.aeron.Subscription;
import org.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.benchmarks.rtt.Configuration;
import uk.co.real_logic.benchmarks.rtt.MessageRecorder;
import uk.co.real_logic.benchmarks.rtt.MessageTransceiver;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronUtil.*;

public abstract class AbstractMessageTransceiver extends MessageTransceiver
{
    private int frameCountLimit;
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

    public AbstractMessageTransceiver(final MessageRecorder messageRecorder)
    {
        super(messageRecorder);
    }

    public void init(final Configuration configuration) throws Exception
    {
        this.publication = createPublication();

        this.subscription = createSubscription();

        while (!subscription.isConnected() || !publication.isConnected())
        {
            yieldUninterruptibly();
        }

        offerBuffer = new UnsafeBuffer(
            allocateDirectAligned(configuration.messageLength(), CACHE_LINE_LENGTH));

        image = subscription.imageAtIndex(0);
        frameCountLimit = frameCountLimit();
    }

    protected abstract ExclusivePublication createPublication();

    protected abstract Subscription createSubscription();

    public void destroy() throws Exception
    {
        closeAll(publication, subscription);
    }

    public int send(final int numberOfMessages, final int length, final long timestamp)
    {
        return sendMessages(publication, offerBuffer, numberOfMessages, length, timestamp);
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
}
