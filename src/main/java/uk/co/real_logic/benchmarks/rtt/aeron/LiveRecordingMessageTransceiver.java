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
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.RecordingEventsAdapter;
import io.aeron.archive.client.RecordingEventsListener;
import org.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.benchmarks.rtt.Configuration;
import uk.co.real_logic.benchmarks.rtt.MessageRecorder;
import uk.co.real_logic.benchmarks.rtt.MessageTransceiver;

import static io.aeron.ChannelUri.addSessionId;
import static io.aeron.archive.client.AeronArchive.NULL_POSITION;
import static io.aeron.archive.client.AeronArchive.connect;
import static io.aeron.archive.codecs.SourceLocation.LOCAL;
import static io.aeron.logbuffer.ControlledFragmentHandler.Action.ABORT;
import static io.aeron.logbuffer.ControlledFragmentHandler.Action.COMMIT;
import static io.aeron.logbuffer.FrameDescriptor.FRAME_ALIGNMENT;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.align;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronUtil.*;

/**
 * Implementation of the {@link uk.co.real_logic.benchmarks.rtt.MessageTransceiver} interface for benchmarking
 * live recording of the remote stream to local archive. Used together with the {@link EchoNode}.
 */
public final class LiveRecordingMessageTransceiver extends MessageTransceiver
{
    private final ArchivingMediaDriver archivingMediaDriver;
    private final AeronArchive aeronArchive;
    private final boolean ownsArchiveClient;

    private ExclusivePublication publication;
    private UnsafeBuffer offerBuffer;
    private long recordingId;

    private int frameCountLimit;

    private Subscription recordingEventsSubscription;
    private RecordingEventsAdapter recordingEventsAdapter;
    private long recordingPosition = NULL_POSITION;
    private long recordingPositionConsumed = NULL_POSITION;

    private Subscription subscription;
    private Image image;
    private int messagesReceived = 0;
    private final ImageControlledFragmentAssembler messageHandler = new ImageControlledFragmentAssembler(
        (buffer, offset, length, header) ->
        {
            if (recordingPositionConsumed == recordingPosition)
            {
                return ABORT;
            }

            onMessageReceived(buffer.getLong(offset, LITTLE_ENDIAN));
            recordingPositionConsumed += align(length, FRAME_ALIGNMENT);
            messagesReceived++;

            return COMMIT;
        });

    public LiveRecordingMessageTransceiver(final MessageRecorder messageRecorder)
    {
        this(launchArchivingMediaDriver(true), connect(), true, messageRecorder);
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

        frameCountLimit = frameCountLimit();

        subscription = aeron.addSubscription(receiveChannel(), receiveStreamId());

        final String sendChannel = sendChannel();
        final int sendStreamId = sendStreamId();
        publication = aeron.addExclusivePublication(sendChannel, sendStreamId);

        final int publicationSessionId = publication.sessionId();
        final String channel = addSessionId(sendChannel, publicationSessionId);
        aeronArchive.startRecording(channel, sendStreamId, LOCAL, true);

        recordingEventsSubscription = aeron.addSubscription(
            context.recordingEventsChannel(), context.recordingEventsStreamId());

        recordingEventsAdapter = new RecordingEventsAdapter(new RecordingEventsListener()
            {
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
                    if (recordingId == LiveRecordingMessageTransceiver.this.recordingId)
                    {
                        if (NULL_POSITION == recordingPositionConsumed)
                        {
                            recordingPositionConsumed = startPosition;
                        }
                        LiveRecordingMessageTransceiver.this.recordingPosition = position;
                    }
                }

                public void onStop(final long recordingId, final long startPosition, final long stopPosition)
                {
                    if (recordingId == LiveRecordingMessageTransceiver.this.recordingId)
                    {
                        LiveRecordingMessageTransceiver.this.recordingPosition = stopPosition;
                    }
                }
            },
            recordingEventsSubscription,
            frameCountLimit);

        recordingId = awaitRecordingStart(aeron, publicationSessionId);

        while (!recordingEventsSubscription.isConnected() || !subscription.isConnected() || !publication.isConnected())
        {
            yieldUninterruptedly();
        }

        offerBuffer = new UnsafeBuffer(allocateDirectAligned(configuration.messageLength(), CACHE_LINE_LENGTH));

        image = subscription.imageAtIndex(0);
    }

    public void destroy()
    {
        closeAll(publication, recordingEventsSubscription, subscription);

        if (ownsArchiveClient)
        {
            closeAll(aeronArchive, archivingMediaDriver);
            archivingMediaDriver.mediaDriver().context().deleteAeronDirectory();
            archivingMediaDriver.archive().context().deleteDirectory();
        }
    }

    public int send(final int numberOfMessages, final int messageLength, final long timestamp)
    {
        return sendMessages(publication, offerBuffer, numberOfMessages, messageLength, timestamp);
    }

    public int receive()
    {
        if (recordingPositionConsumed == recordingPosition)
        {
            recordingEventsAdapter.poll();
            if (recordingPositionConsumed == recordingPosition)
            {
                return 0; // no new recording events
            }
        }

        messagesReceived = 0;
        final Image image = this.image;
        final int fragments = image.controlledPoll(messageHandler, frameCountLimit);
        if (0 == fragments && image.isClosed())
        {
            throw new IllegalStateException("image closed unexpectedly");
        }

        return messagesReceived;
    }
}
