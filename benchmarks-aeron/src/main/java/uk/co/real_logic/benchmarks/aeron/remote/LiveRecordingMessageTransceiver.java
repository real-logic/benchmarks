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

import io.aeron.Aeron;
import io.aeron.Image;
import io.aeron.ImageControlledFragmentAssembler;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.RecordingEventsAdapter;
import io.aeron.archive.client.RecordingEventsListener;
import io.aeron.logbuffer.ControlledFragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.benchmarks.remote.Configuration;
import uk.co.real_logic.benchmarks.remote.MessageRecorder;

import static io.aeron.ChannelUri.addSessionId;
import static io.aeron.archive.client.AeronArchive.NULL_POSITION;
import static io.aeron.archive.client.AeronArchive.connect;
import static io.aeron.archive.codecs.SourceLocation.LOCAL;
import static io.aeron.logbuffer.ControlledFragmentHandler.Action.ABORT;
import static io.aeron.logbuffer.ControlledFragmentHandler.Action.COMMIT;
import static io.aeron.logbuffer.FrameDescriptor.FRAME_ALIGNMENT;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.BitUtil.*;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

/**
 * Implementation of the {@link uk.co.real_logic.benchmarks.remote.MessageTransceiver} interface for benchmarking
 * live recording of the remote stream to local archive. Used together with the {@link EchoNode}.
 */
public final class LiveRecordingMessageTransceiver
    extends MessageTransceiverProducerStatePadded implements ControlledFragmentHandler
{
    private long recordingPosition = NULL_POSITION;
    private long recordingPositionConsumed = NULL_POSITION;
    private long recordingId;
    private final boolean ownsArchiveClient;

    private final ImageControlledFragmentAssembler messageHandler = new ImageControlledFragmentAssembler(this);
    private final ArchivingMediaDriver archivingMediaDriver;
    private final AeronArchive aeronArchive;
    private Subscription recordingEventsSubscription;
    private RecordingEventsAdapter recordingEventsAdapter;
    private Subscription subscription;
    private Image image;

    public LiveRecordingMessageTransceiver(final MessageRecorder messageRecorder)
    {
        this(launchArchivingMediaDriver(), connect(), true, messageRecorder);
    }

    LiveRecordingMessageTransceiver(
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
        final AeronArchive.Context context = aeronArchive.context();
        final Aeron aeron = context.aeron();

        subscription = aeron.addSubscription(sourceChannel(), sourceStreamId());

        final String sendChannel = destinationChannel();
        final int sendStreamId = destinationStreamId();
        publication = aeron.addExclusivePublication(sendChannel, sendStreamId);

        recordingEventsSubscription = aeron.addSubscription(
            context.recordingEventsChannel(), context.recordingEventsStreamId());

        recordingEventsAdapter = new RecordingEventsAdapter(
            new LiveRecordingEventsListener(this), recordingEventsSubscription, FRAGMENT_LIMIT);

        while (!recordingEventsSubscription.isConnected() || !subscription.isConnected() || !publication.isConnected())
        {
            yieldUninterruptedly();
        }

        final int publicationSessionId = publication.sessionId();
        final String channel = addSessionId(sendChannel, publicationSessionId);
        aeronArchive.startRecording(channel, sendStreamId, LOCAL, true);
        recordingId = awaitRecordingStart(aeron, publicationSessionId);

        offerBuffer = new UnsafeBuffer(allocateDirectAligned(configuration.messageLength(), CACHE_LINE_LENGTH));
        image = subscription.imageAtIndex(0);
    }

    public void destroy()
    {
        closeAll(publication, recordingEventsSubscription, subscription);

        if (ownsArchiveClient)
        {
            closeAll(aeronArchive, archivingMediaDriver);
        }
    }

    public int send(final int numberOfMessages, final int messageLength, final long timestamp, final long checksum)
    {
        return sendMessages(publication, offerBuffer, numberOfMessages, messageLength, timestamp, checksum);
    }

    public void receive()
    {
        if (recordingPositionConsumed == recordingPosition)
        {
            recordingEventsAdapter.poll();
            if (recordingPositionConsumed == recordingPosition)
            {
                return; // no new recording events
            }
        }

        final int fragments = image.controlledPoll(messageHandler, FRAGMENT_LIMIT);
        if (0 == fragments && image.isClosed())
        {
            throw new IllegalStateException("image closed unexpectedly");
        }
    }

    public ControlledFragmentHandler.Action onFragment(
        final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        if (recordingPositionConsumed == recordingPosition)
        {
            return ABORT;
        }

        final long timestamp = buffer.getLong(offset, LITTLE_ENDIAN);
        final long checksum = buffer.getLong(offset + length - SIZE_OF_LONG, LITTLE_ENDIAN);
        onMessageReceived(timestamp, checksum);
        recordingPositionConsumed += align(length, FRAME_ALIGNMENT);

        return COMMIT;
    }

    static final class LiveRecordingEventsListener implements RecordingEventsListener
    {
        private final LiveRecordingMessageTransceiver messageTransceiver;

        LiveRecordingEventsListener(final LiveRecordingMessageTransceiver messageTransceiver)
        {
            this.messageTransceiver = messageTransceiver;
        }

        public void onStart(
            final long recordingId,
            final long startPosition,
            final int sessionId,
            final int streamId,
            final String channel,
            final String sourceIdentity)
        {
        }

        public void onProgress(final long recordingId, final long startPosition, final long position)
        {
            if (recordingId == messageTransceiver.recordingId)
            {
                if (NULL_POSITION == messageTransceiver.recordingPositionConsumed)
                {
                    messageTransceiver.recordingPositionConsumed = startPosition;
                }

                messageTransceiver.recordingPosition = position;
            }
        }

        public void onStop(final long recordingId, final long startPosition, final long stopPosition)
        {
            if (recordingId == messageTransceiver.recordingId)
            {
                messageTransceiver.recordingPosition = stopPosition;
            }
        }
    }
}
