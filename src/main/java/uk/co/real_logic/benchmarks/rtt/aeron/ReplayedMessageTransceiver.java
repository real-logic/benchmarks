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
import io.aeron.archive.client.RecordingDescriptorConsumer;
import io.aeron.driver.MediaDriver;
import org.agrona.collections.MutableLong;
import org.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.benchmarks.rtt.Configuration;
import uk.co.real_logic.benchmarks.rtt.MessageRecorder;
import uk.co.real_logic.benchmarks.rtt.MessageTransceiver;

import static io.aeron.ChannelUri.addSessionId;
import static io.aeron.archive.client.AeronArchive.connect;
import static java.lang.Long.MAX_VALUE;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronUtil.*;
import static uk.co.real_logic.benchmarks.rtt.aeron.PlainMessageTransceiver.sendMessages;

public final class ReplayedMessageTransceiver extends MessageTransceiver
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

    public ReplayedMessageTransceiver(final MessageRecorder messageRecorder)
    {
        this(launchEmbeddedMediaDriverIfConfigured(), connect(), true, messageRecorder);
    }

    ReplayedMessageTransceiver(
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

    public void init(final Configuration configuration) throws Exception
    {
        final Aeron aeron = aeronArchive.context().aeron();

        publication = aeron.addExclusivePublication(senderChannel(), senderStreamId());

        final String receiverChannel = receiverChannel();
        final int receiverStreamId = receiverStreamId();
        final int replayStreamId = replayStreamId();
        final long recordingId = findLatestRecording(receiverChannel, receiverStreamId);

        replaySessionId = aeronArchive.startReplay(recordingId, 0, MAX_VALUE, receiverChannel, replayStreamId);
        final String channel = addSessionId(receiverChannel, (int)replaySessionId);

        subscription = aeron.addSubscription(channel, replayStreamId);

        while (!subscription.isConnected() || !publication.isConnected())
        {
            Thread.yield();
        }

        offerBuffer = new UnsafeBuffer(
            allocateDirectAligned(configuration.messageLength(), CACHE_LINE_LENGTH));

        image = subscription.imageAtIndex(0);
        frameCountLimit = frameCountLimit();
    }

    public void destroy() throws Exception
    {
        aeronArchive.stopReplay(replaySessionId);

        closeAll(publication, subscription);

        if (ownsArchiveClient)
        {
            closeAll(aeronArchive, mediaDriver);
        }
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

    private long findLatestRecording(final String recordingChannel, final int recordingStreamId)
    {
        final MutableLong lastRecordingId = new MutableLong();

        final RecordingDescriptorConsumer consumer =
            (controlSessionId,
            correlationId,
            recordingId,
            startTimestamp,
            stopTimestamp,
            startPosition,
            stopPosition,
            initialTermId,
            segmentFileLength,
            termBufferLength,
            mtuLength,
            sessionId,
            streamId,
            strippedChannel,
            originalChannel,
            sourceIdentity) -> lastRecordingId.set(recordingId);

        final int foundCount = aeronArchive
            .listRecordingsForUri(0, 1, recordingChannel, recordingStreamId, consumer);

        if (foundCount == 0)
        {
            throw new IllegalStateException("no recordings found");
        }

        return lastRecordingId.get();
    }

}
