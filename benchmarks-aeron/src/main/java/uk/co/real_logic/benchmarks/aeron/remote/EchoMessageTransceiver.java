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
package uk.co.real_logic.benchmarks.aeron.remote;

import io.aeron.*;
import io.aeron.driver.MediaDriver;
import org.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.benchmarks.remote.Configuration;
import uk.co.real_logic.benchmarks.remote.MessageRecorder;

import java.util.Arrays;

import static io.aeron.Aeron.connect;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

public final class EchoMessageTransceiver extends EchoMessageTransceiverProducerStatePadded
{
    static final int NUMBER_OF_KEEP_ALIVE_MESSAGES = 1;
    static final int KEEP_ALIVE_MESSAGE_LENGTH = 32;

    private final MediaDriver mediaDriver;
    private final Aeron aeron;
    private final boolean ownsAeronClient;

    private Subscription subscription;
    private Image image;
    private final FragmentAssembler dataHandler = new FragmentAssembler(
        (buffer, offset, length, header) ->
        {
            final long timestamp = buffer.getLong(offset, LITTLE_ENDIAN);
            final long checksum = buffer.getLong(offset + length - SIZE_OF_LONG, LITTLE_ENDIAN);
            onMessageReceived(timestamp, checksum);
        });

    public EchoMessageTransceiver(final MessageRecorder messageRecorder)
    {
        this(launchEmbeddedMediaDriverIfConfigured(), connect(), true, messageRecorder);
    }

    EchoMessageTransceiver(
        final MediaDriver mediaDriver,
        final Aeron aeron,
        final boolean ownsAeronClient,
        final MessageRecorder messageRecorder)
    {
        super(messageRecorder);
        this.mediaDriver = mediaDriver;
        this.aeron = aeron;
        this.ownsAeronClient = ownsAeronClient;
    }

    public void init(final Configuration configuration)
    {
        publication = aeron.addExclusivePublication(destinationChannels()[0], destinationStreams()[0]);
        subscription = aeron.addSubscription(sourceChannels()[0], sourceStreams()[0]);

        final String[] passiveChannels = passiveChannels();
        final int[] passiveStreams = passiveStreams();
        if (passiveChannels.length != passiveStreams.length)
        {
            throw new IllegalStateException("Number of passive channels does not match with passive streams: " +
                Arrays.toString(passiveChannels) + ", " + Arrays.toString(passiveStreams));
        }

        passivePublications = EMPTY_PUBLICATIONS;
        if (passiveChannels.length > 0)
        {
            passivePublications = new ExclusivePublication[passiveChannels.length];
            for (int i = 0; i < passiveChannels.length; i++)
            {
                passivePublications[i] = aeron.addExclusivePublication(passiveChannels[i], passiveStreams[i]);
            }
            keepAliveIntervalNs = passiveChannelsKeepAliveIntervalNanos();
        }

        while (!subscription.isConnected() || !publication.isConnected() || !allConnected(passivePublications))
        {
            yieldUninterruptedly();
        }

        offerBuffer = new UnsafeBuffer(allocateDirectAligned(configuration.messageLength(), CACHE_LINE_LENGTH));

        image = subscription.imageAtIndex(0);
    }

    public void destroy()
    {
        closeAll(passivePublications);
        closeAll(publication, subscription);

        if (ownsAeronClient)
        {
            closeAll(aeron, mediaDriver);
        }
    }

    public int send(final int numberOfMessages, final int messageLength, final long timestamp, final long checksum)
    {
        final int sent =
            sendMessages(publication, offerBuffer, numberOfMessages, messageLength, timestamp, checksum);

        if ((timeOfLastKeepAliveNs + keepAliveIntervalNs) - timestamp < 0)
        {
            sendKeepAliveMessages(timestamp, checksum);
            timeOfLastKeepAliveNs = timestamp;
        }
        return sent;
    }

    public void receive()
    {
        final int fragments = image.poll(dataHandler, FRAGMENT_LIMIT);
        if (0 == fragments && image.isClosed())
        {
            throw new IllegalStateException("image closed unexpectedly");
        }
    }

    private void sendKeepAliveMessages(final long timestamp, final long checksum)
    {
        final ExclusivePublication[] passivePublications = this.passivePublications;
        for (int i = 0; i < passivePublications.length; i++)
        {
            sendMessages(
                passivePublications[i],
                offerBuffer,
                NUMBER_OF_KEEP_ALIVE_MESSAGES,
                KEEP_ALIVE_MESSAGE_LENGTH,
                timestamp,
                checksum);
        }
    }
}
