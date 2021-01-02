/*
 * Copyright 2015-2021 Real Logic Limited.
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
import io.aeron.logbuffer.BufferClaim;
import uk.co.real_logic.benchmarks.remote.Configuration;
import uk.co.real_logic.benchmarks.remote.MessageRecorder;
import uk.co.real_logic.benchmarks.remote.MessageTransceiver;

import java.util.Arrays;

import static io.aeron.Aeron.connect;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

public final class EchoMessageTransceiver extends MessageTransceiver
{
    static final int NUMBER_OF_KEEP_ALIVE_MESSAGES = 1;
    static final int KEEP_ALIVE_MESSAGE_LENGTH = 32;

    private final MediaDriver mediaDriver;
    private final Aeron aeron;
    private final boolean ownsAeronClient;

    private final BufferClaim bufferClaim = new BufferClaim();
    ExclusivePublication[] publications;
    ExclusivePublication[] passivePublications;
    long keepAliveIntervalNs;
    long timeOfLastKeepAliveNs;
    int sendIndex;

    private Subscription[] subscriptions;
    private Image[] images;
    private int receiveIndex;
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
        final String[] destinationChannels = destinationChannels();
        final int[] destinationStreams = destinationStreams();
        assertChannelsAndStreamsMatch(
            destinationChannels, destinationStreams, DESTINATION_CHANNELS_PROP_NAME, DESTINATION_STREAMS_PROP_NAME);

        final String[] sourceChannels = sourceChannels();
        final int[] sourceStreams = sourceStreams();
        assertChannelsAndStreamsMatch(
            sourceChannels, sourceStreams, SOURCE_CHANNELS_PROP_NAME, SOURCE_STREAMS_PROP_NAME);

        if (destinationChannels.length != sourceChannels.length)
        {
            throw new IllegalArgumentException("Number of destinations does not match the number of sources:\n " +
                Arrays.toString(destinationChannels) + "\n " + Arrays.toString(sourceChannels));
        }

        final String[] passiveChannels = passiveChannels();
        final int[] passiveStreams = passiveStreams();
        assertChannelsAndStreamsMatch(
            passiveChannels, passiveStreams, PASSIVE_CHANNELS_PROP_NAME, PASSIVE_STREAMS_PROP_NAME);

        final int numActiveChannels = destinationChannels.length;
        publications = new ExclusivePublication[numActiveChannels];
        subscriptions = new Subscription[numActiveChannels];
        images = new Image[numActiveChannels];
        for (int i = 0; i < numActiveChannels; i++)
        {
            publications[i] = aeron.addExclusivePublication(destinationChannels[i], destinationStreams[i]);
            subscriptions[i] = aeron.addSubscription(sourceChannels[i], sourceStreams[i]);
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

        while (!allConnected(subscriptions) || !allConnected(publications) || !allConnected(passivePublications))
        {
            yieldUninterruptedly();
        }

        for (int i = 0; i < numActiveChannels; i++)
        {
            images[i] = subscriptions[i].imageAtIndex(0);
        }
    }

    public void destroy()
    {
        closeAll(passivePublications);
        closeAll(publications);
        closeAll(subscriptions);

        if (ownsAeronClient)
        {
            closeAll(aeron, mediaDriver);
        }
    }

    public int send(final int numberOfMessages, final int messageLength, final long timestamp, final long checksum)
    {
        final ExclusivePublication[] publications = this.publications;
        int index = sendIndex++;
        if (index >= publications.length)
        {
            sendIndex = index = 0;
        }

        final int sent =
            sendMessages(publications[index], bufferClaim, numberOfMessages, messageLength, timestamp, checksum);

        if ((timeOfLastKeepAliveNs + keepAliveIntervalNs) - timestamp < 0)
        {
            sendKeepAliveMessages(timestamp, checksum);
            timeOfLastKeepAliveNs = timestamp;
        }
        return sent;
    }

    public void receive()
    {
        final Image[] images = this.images;
        final int length = images.length;
        final FragmentAssembler dataHandler = this.dataHandler;
        int startingIndex = receiveIndex++;
        if (startingIndex >= length)
        {
            receiveIndex = startingIndex = 0;
        }

        int fragments = 0;
        for (int i = startingIndex; i < length && fragments < FRAGMENT_LIMIT; i++)
        {
            fragments += images[i].poll(dataHandler, FRAGMENT_LIMIT - fragments);
        }

        for (int i = 0; i < startingIndex && fragments < FRAGMENT_LIMIT; i++)
        {
            fragments += images[i].poll(dataHandler, FRAGMENT_LIMIT - fragments);
        }
    }

    private void sendKeepAliveMessages(final long timestamp, final long checksum)
    {
        for (final ExclusivePublication passivePublication : passivePublications)
        {
            sendMessages(
                passivePublication,
                bufferClaim,
                NUMBER_OF_KEEP_ALIVE_MESSAGES,
                KEEP_ALIVE_MESSAGE_LENGTH,
                timestamp,
                checksum);
        }
    }
}
