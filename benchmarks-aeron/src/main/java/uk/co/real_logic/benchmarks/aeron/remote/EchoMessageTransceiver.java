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
import uk.co.real_logic.benchmarks.remote.MessageTransceiver;

import java.util.Arrays;

import static io.aeron.Aeron.connect;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

abstract class EchoMessageTransceiverProducerState extends MessageTransceiver
{
    UnsafeBuffer offerBuffer;
    ExclusivePublication[] publications;
    ExclusivePublication[] passivePublications;
    long keepAliveIntervalNs;
    long timeOfLastKeepAliveNs;
    int sendIndex;

    EchoMessageTransceiverProducerState(final MessageRecorder messageRecorder)
    {
        super(messageRecorder);
    }
}

@SuppressWarnings("unused")
abstract class EchoMessageTransceiverProducerStatePadded extends EchoMessageTransceiverProducerState
{
    byte p000, p001, p002, p003, p004, p005, p006, p007, p008, p009, p010, p011, p012, p013, p014, p015;
    byte p016, p017, p018, p019, p020, p021, p022, p023, p024, p025, p026, p027, p028, p029, p030, p031;
    byte p032, p033, p034, p035, p036, p037, p038, p039, p040, p041, p042, p043, p044, p045, p046, p047;
    byte p048, p049, p050, p051, p052, p053, p054, p055, p056, p057, p058, p059, p060, p061, p062, p063;

    EchoMessageTransceiverProducerStatePadded(final MessageRecorder messageRecorder)
    {
        super(messageRecorder);
    }
}

public final class EchoMessageTransceiver extends EchoMessageTransceiverProducerStatePadded
{
    static final int NUMBER_OF_KEEP_ALIVE_MESSAGES = 1;
    static final int KEEP_ALIVE_MESSAGE_LENGTH = 32;

    private final MediaDriver mediaDriver;
    private final Aeron aeron;
    private final boolean ownsAeronClient;

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

        offerBuffer = new UnsafeBuffer(allocateDirectAligned(configuration.messageLength(), CACHE_LINE_LENGTH));

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
            sendMessages(publications[index], offerBuffer, numberOfMessages, messageLength, timestamp, checksum);

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
