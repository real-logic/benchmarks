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

import io.aeron.*;
import io.aeron.archive.client.AeronArchive;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.BufferClaim;
import org.agrona.concurrent.SystemNanoClock;
import uk.co.real_logic.benchmarks.remote.Configuration;
import uk.co.real_logic.benchmarks.remote.MessageTransceiver;

import static io.aeron.ChannelUri.addSessionId;
import static io.aeron.archive.client.AeronArchive.connect;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

public final class LiveReplayMessageTransceiver extends MessageTransceiver
{
    private final MediaDriver mediaDriver;
    private final AeronArchive aeronArchive;
    private final boolean ownsArchiveClient;

    private ExclusivePublication publication;
    private final BufferClaim bufferClaim = new BufferClaim();

    private Subscription subscription;
    private Image image;
    private final FragmentAssembler dataHandler = new FragmentAssembler(
        (buffer, offset, length, header) ->
        {
            final long timestamp = buffer.getLong(offset, LITTLE_ENDIAN);
            final long checksum = buffer.getLong(offset + length - SIZE_OF_LONG, LITTLE_ENDIAN);
            onMessageReceived(timestamp, checksum);
        });

    public LiveReplayMessageTransceiver()
    {
        this(launchEmbeddedMediaDriverIfConfigured(), connect(), true);
    }

    LiveReplayMessageTransceiver(
        final MediaDriver mediaDriver,
        final AeronArchive aeronArchive,
        final boolean ownsArchiveClient)
    {
        this.mediaDriver = mediaDriver;
        this.aeronArchive = aeronArchive;
        this.ownsArchiveClient = ownsArchiveClient;
    }

    public void init(final Configuration configuration)
    {
        final Aeron aeron = aeronArchive.context().aeron();

        publication = aeron.addExclusivePublication(destinationChannels()[0], destinationStreams()[0]);

        final long connectionTimeoutNs = connectionTimeoutNs();
        final SystemNanoClock clock = SystemNanoClock.INSTANCE;
        awaitConnected(publication::isConnected, connectionTimeoutNs, clock);

        final long recordingId = findLastRecordingId(aeronArchive, archiveChannel(), archiveStream());

        final String replayChannel = sourceChannels()[0];
        final int replayStreamId = sourceStreams()[0];
        final long replaySessionId = replayFullRecording(aeronArchive, recordingId, replayChannel, replayStreamId);

        final String channel = addSessionId(replayChannel, (int)replaySessionId);
        subscription = aeron.addSubscription(channel, replayStreamId);

        awaitConnected(subscription::isConnected, connectionTimeoutNs, clock);

        image = subscription.imageAtIndex(0);
    }

    public void destroy()
    {
        closeAll(publication, subscription);

        if (ownsArchiveClient)
        {
            closeAll(aeronArchive, mediaDriver);
        }
    }

    public int send(final int numberOfMessages, final int messageLength, final long timestamp, final long checksum)
    {
        return sendMessages(publication, bufferClaim, numberOfMessages, messageLength, timestamp, checksum);
    }

    public void receive()
    {
        final int fragments = image.poll(dataHandler, FRAGMENT_LIMIT);
        if (0 == fragments && image.isClosed())
        {
            throw new IllegalStateException("image closed unexpectedly");
        }
    }
}
