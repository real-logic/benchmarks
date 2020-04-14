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
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.ArchiveException;
import io.aeron.driver.MediaDriver;
import org.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.benchmarks.rtt.Configuration;
import uk.co.real_logic.benchmarks.rtt.MessageRecorder;
import uk.co.real_logic.benchmarks.rtt.MessageTransceiver;

import static io.aeron.ChannelUri.addSessionId;
import static io.aeron.archive.client.AeronArchive.connect;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronUtil.*;

public final class LiveReplayMessageTransceiver extends MessageTransceiver
{
    private final MediaDriver mediaDriver;
    private final AeronArchive aeronArchive;
    private final boolean ownsArchiveClient;
    private ExclusivePublication publication;
    private UnsafeBuffer offerBuffer;

    private long replaySessionId;
    private Subscription subscription;
    private Image image;
    private int messagesReceived;
    private int frameCountLimit;
    private final FragmentAssembler dataHandler = new FragmentAssembler(
        (buffer, offset, length, header) ->
        {
            onMessageReceived(buffer.getLong(offset, LITTLE_ENDIAN));
            messagesReceived++;
        });

    public LiveReplayMessageTransceiver(final MessageRecorder messageRecorder)
    {
        this(launchEmbeddedMediaDriverIfConfigured(), connect(), true, messageRecorder);
    }

    LiveReplayMessageTransceiver(
        final MediaDriver mediaDriver,
        final AeronArchive aeronArchive,
        final boolean ownsArchiveClient,
        final MessageRecorder messageRecorder)
    {
        super(messageRecorder);
        this.mediaDriver = mediaDriver;
        this.aeronArchive = aeronArchive;
        this.ownsArchiveClient = ownsArchiveClient;
    }

    public void init(final Configuration configuration)
    {
        final Aeron aeron = aeronArchive.context().aeron();

        publication = aeron.addExclusivePublication(sendChannel(), sendStreamId());

        while (!publication.isConnected())
        {
            yieldUninterruptedly();
        }

        offerBuffer = new UnsafeBuffer(allocateDirectAligned(configuration.messageLength(), CACHE_LINE_LENGTH));

        frameCountLimit = frameCountLimit();
    }

    public void destroy()
    {
        try
        {
            aeronArchive.stopReplay(replaySessionId);
        }
        catch (final ArchiveException ex)
        {
            System.out.println("WARN: " + ex.toString());
        }

        closeAll(publication, subscription);

        if (ownsArchiveClient)
        {
            closeAll(aeronArchive, mediaDriver);
            if (null != mediaDriver)
            {
                mediaDriver.context().deleteAeronDirectory();
            }
        }
    }

    public int send(final int numberOfMessages, final int messageLength, final long timestamp)
    {
        return sendMessages(publication, offerBuffer, numberOfMessages, messageLength, timestamp);
    }

    public int receive()
    {
        Image image = this.image;
        if (null == image)
        {
            startReplay();
            image = this.image;
        }

        messagesReceived = 0;
        final int fragments = image.poll(dataHandler, frameCountLimit);
        if (0 == fragments && image.isClosed())
        {
            throw new IllegalStateException("image closed unexpectedly");
        }

        return messagesReceived;
    }

    private void startReplay()
    {
        final long recordingId = findLastRecordingId(aeronArchive, archiveChannel(), archiveStreamId());

        final String replayChannel = receiveChannel();
        final int replayStreamId = receiveStreamId();
        replaySessionId = replayFullRecording(aeronArchive, recordingId, replayChannel, replayStreamId);

        final String channel = addSessionId(replayChannel, (int)replaySessionId);
        subscription = aeronArchive.context().aeron().addSubscription(channel, replayStreamId);

        while (!subscription.isConnected())
        {
            yieldUninterruptedly();
        }

        this.image = subscription.imageAtIndex(0);
    }
}
