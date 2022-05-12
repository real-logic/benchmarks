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
package uk.co.real_logic.benchmarks.aeron.remote;

import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.FragmentAssembler;
import io.aeron.Image;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.BufferClaim;
import org.HdrHistogram.ValueRecorder;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.SystemNanoClock;
import uk.co.real_logic.benchmarks.remote.Configuration;
import uk.co.real_logic.benchmarks.remote.MessageTransceiver;

import java.util.Arrays;

import static io.aeron.Aeron.connect;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

public final class EchoMessageTransceiver extends MessageTransceiver
{
    private final MediaDriver mediaDriver;
    private final Aeron aeron;
    private final boolean ownsAeronClient;

    private final BufferClaim bufferClaim = new BufferClaim();
    ExclusivePublication[] publications;
    long keepAliveIntervalNs;
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

    public EchoMessageTransceiver(final NanoClock nanoClock, final ValueRecorder valueRecorder)
    {
        this(nanoClock, valueRecorder, launchEmbeddedMediaDriverIfConfigured(), connect(), true);
    }

    EchoMessageTransceiver(
        final NanoClock nanoClock,
        final ValueRecorder valueRecorder,
        final MediaDriver mediaDriver,
        final Aeron aeron,
        final boolean ownsAeronClient)
    {
        super(nanoClock, valueRecorder);
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

        final int numActiveChannels = destinationChannels.length;
        publications = new ExclusivePublication[numActiveChannels];
        subscriptions = new Subscription[numActiveChannels];
        images = new Image[numActiveChannels];
        for (int i = 0; i < numActiveChannels; i++)
        {
            publications[i] = aeron.addExclusivePublication(destinationChannels[i], destinationStreams[i]);
            subscriptions[i] = aeron.addSubscription(sourceChannels[i], sourceStreams[i]);
        }

        awaitConnected(
            () -> allConnected(subscriptions) && allConnected(publications),
            connectionTimeoutNs(),
            SystemNanoClock.INSTANCE);

        for (int i = 0; i < numActiveChannels; i++)
        {
            images[i] = subscriptions[i].imageAtIndex(0);
        }
    }

    public void destroy()
    {
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

        return sendMessages(
            publications[index], bufferClaim, numberOfMessages, messageLength, timestamp, checksum);
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
}
