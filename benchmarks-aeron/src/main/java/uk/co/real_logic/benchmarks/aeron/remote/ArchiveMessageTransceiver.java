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
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.archive.client.AeronArchive;
import org.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.benchmarks.rtt.Configuration;
import uk.co.real_logic.benchmarks.rtt.MessageRecorder;
import uk.co.real_logic.benchmarks.rtt.MessageTransceiver;

import static io.aeron.ChannelUri.addSessionId;
import static io.aeron.archive.client.AeronArchive.connect;
import static io.aeron.archive.codecs.SourceLocation.LOCAL;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

public final class ArchiveMessageTransceiver extends MessageTransceiver
{
    private final ArchivingMediaDriver archivingMediaDriver;
    private final AeronArchive aeronArchive;
    private final boolean ownsArchiveClient;

    private ExclusivePublication publication;
    private UnsafeBuffer offerBuffer;

    private Subscription subscription;
    private Image image;
    private int frameCountLimit;
    private final FragmentAssembler dataHandler = new FragmentAssembler(
        (buffer, offset, length, header) ->
        {
            final long timestamp = buffer.getLong(offset, LITTLE_ENDIAN);
            final long checksum = buffer.getLong(offset + length - SIZE_OF_LONG, LITTLE_ENDIAN);
            onMessageReceived(timestamp, checksum);
        });

    public ArchiveMessageTransceiver(final MessageRecorder messageRecorder)
    {
        this(launchArchivingMediaDriver(false), connect(), true, messageRecorder);
    }

    ArchiveMessageTransceiver(
        final ArchivingMediaDriver archivingMediaDriver,
        final AeronArchive aeronArchive,
        final boolean ownsArchiveClient,
        final MessageRecorder messageRecorder)
    {
        super(messageRecorder);
        this.archivingMediaDriver = archivingMediaDriver;
        this.aeronArchive = aeronArchive;
        this.ownsArchiveClient = ownsArchiveClient;
    }

    public void init(final Configuration configuration)
    {
        final Aeron aeron = aeronArchive.context().aeron();

        this.subscription = aeron.addSubscription(receiveChannel(), receiveStreamId());

        final String archiveChannel = archiveChannel();
        final int archiveStreamId = archiveStreamId();
        publication = aeron.addExclusivePublication(archiveChannel, archiveStreamId);

        final int publicationSessionId = publication.sessionId();
        final String channel = addSessionId(archiveChannel, publicationSessionId);
        aeronArchive.startRecording(channel, archiveStreamId, LOCAL, true);

        while (!subscription.isConnected() || !publication.isConnected())
        {
            yieldUninterruptedly();
        }

        awaitRecordingStart(aeron, publicationSessionId);

        offerBuffer = new UnsafeBuffer(allocateDirectAligned(configuration.messageLength(), CACHE_LINE_LENGTH));

        this.image = subscription.imageAtIndex(0);

        frameCountLimit = frameCountLimit();
    }

    public void destroy()
    {
        closeAll(publication, subscription);

        if (ownsArchiveClient)
        {
            closeAll(aeronArchive, archivingMediaDriver);
            archivingMediaDriver.mediaDriver().context().deleteAeronDirectory();
            archivingMediaDriver.archive().context().deleteDirectory();
        }
    }

    public int send(final int numberOfMessages, final int messageLength, final long timestamp, final long checksum)
    {
        return sendMessages(publication, offerBuffer, numberOfMessages, messageLength, timestamp, checksum);
    }

    public void receive()
    {
        final Image image = this.image;
        final int fragments = image.poll(dataHandler, frameCountLimit);
        if (0 == fragments && image.isClosed())
        {
            throw new IllegalStateException("image closed unexpectedly");
        }
    }
}
