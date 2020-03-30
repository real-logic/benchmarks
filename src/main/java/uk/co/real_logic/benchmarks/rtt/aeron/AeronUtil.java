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
import io.aeron.archive.Archive;
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.RecordingDescriptorConsumer;
import io.aeron.driver.MediaDriver;
import io.aeron.exceptions.AeronException;
import org.agrona.collections.MutableLong;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersReader;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.CommonContext.IPC_CHANNEL;
import static io.aeron.Publication.*;
import static io.aeron.archive.status.RecordingPos.findCounterIdBySession;
import static io.aeron.archive.status.RecordingPos.getRecordingId;
import static java.lang.Boolean.getBoolean;
import static java.lang.Integer.getInteger;
import static java.lang.System.getProperty;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.concurrent.status.CountersReader.NULL_COUNTER_ID;

final class AeronUtil
{
    static final String SEND_CHANNEL_PROP_NAME = "aeron.benchmarks.rtt.aeron.send.channel";
    static final String SEND_STREAM_ID_PROP_NAME = "aeron.benchmarks.rtt.aeron.send.streamId";
    static final String RECEIVE_CHANNEL_PROP_NAME = "aeron.benchmarks.rtt.aeron.receive.channel";
    static final String RECEIVE_STREAM_ID_PROP_NAME = "aeron.benchmarks.rtt.aeron.receive.streamId";
    static final String ARCHIVE_CHANNEL_PROP_NAME = "aeron.benchmarks.rtt.aeron.archive.channel";
    static final String ARCHIVE_STREAM_ID_PROP_NAME = "aeron.benchmarks.rtt.aeron.archive.streamId";
    static final String EMBEDDED_MEDIA_DRIVER_PROP_NAME = "aeron.benchmarks.rtt.aeron.embeddedMediaDriver";
    static final String FRAME_COUNT_LIMIT_PROP_NAME = "aeron.benchmarks.rtt.aeron.frameCountLimit";

    private AeronUtil()
    {
    }

    static String sendChannel()
    {
        return getProperty(SEND_CHANNEL_PROP_NAME, "aeron:udp?endpoint=localhost:33333");
    }

    static int sendStreamId()
    {
        return getInteger(SEND_STREAM_ID_PROP_NAME, 1_000_000_000);
    }

    static String receiveChannel()
    {
        return getProperty(RECEIVE_CHANNEL_PROP_NAME, "aeron:udp?endpoint=localhost:33334");
    }

    static int receiveStreamId()
    {
        return getInteger(RECEIVE_STREAM_ID_PROP_NAME, 1_000_000_001);
    }

    static String archiveChannel()
    {
        return getProperty(ARCHIVE_CHANNEL_PROP_NAME, IPC_CHANNEL);
    }

    static int archiveStreamId()
    {
        return getInteger(ARCHIVE_STREAM_ID_PROP_NAME, 1_000_000_002);
    }

    static boolean embeddedMediaDriver()
    {
        return getBoolean(EMBEDDED_MEDIA_DRIVER_PROP_NAME);
    }

    static int frameCountLimit()
    {
        return getInteger(FRAME_COUNT_LIMIT_PROP_NAME, 10);
    }

    static MediaDriver launchEmbeddedMediaDriverIfConfigured()
    {
        if (embeddedMediaDriver())
        {
            final MediaDriver.Context mediaDriverContext = new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .spiesSimulateConnection(true);
            return MediaDriver.launch(mediaDriverContext);
        }
        return null;
    }

    static ArchivingMediaDriver launchArchivingMediaDriver(final boolean recordingEventsEnabled)
    {
        final MediaDriver.Context mediaDriverCtx = new MediaDriver.Context()
            .dirDeleteOnStart(true)
            .spiesSimulateConnection(true);
        final Archive.Context archiveCtx = new Archive.Context()
            .aeronDirectoryName(mediaDriverCtx.aeronDirectoryName())
            .recordingEventsEnabled(recordingEventsEnabled)
            .deleteArchiveOnStart(true);
        return ArchivingMediaDriver.launch(
            mediaDriverCtx,
            archiveCtx);
    }

    static long awaitRecordingStart(final Aeron aeron, final int publicationSessionId)
    {
        final CountersReader counters = aeron.countersReader();
        int counterId;
        do
        {
            counterId = findCounterIdBySession(counters, publicationSessionId);
        }
        while (NULL_COUNTER_ID == counterId);

        return getRecordingId(counters, counterId);
    }

    static long findLastRecordingId(
        final AeronArchive aeronArchive, final String recordingChannel, final int recordingStreamId)
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

        int foundCount;
        do
        {
            foundCount = aeronArchive.listRecordingsForUri(0, 1, recordingChannel, recordingStreamId, consumer);
        }
        while (0 == foundCount);

        return lastRecordingId.get();
    }

    static void publishLoop(
        final ExclusivePublication publication, final Subscription subscription, final AtomicBoolean running)
    {
        final FragmentAssembler dataHandler = new FragmentAssembler(
            (buffer, offset, length, header) ->
            {
                long result;
                while ((result = publication.offer(buffer, offset, length)) < 0L)
                {
                    checkPublicationResult(result);
                }
            });

        final Image image = subscription.imageAtIndex(0);
        final int frameCountLimit = frameCountLimit();
        while (running.get())
        {
            final int fragments = image.poll(dataHandler, frameCountLimit);
            if (0 == fragments && image.isClosed())
            {
                throw new IllegalStateException("image closed");
            }
        }
    }

    static int sendMessages(
        final ExclusivePublication publication,
        final UnsafeBuffer offerBuffer,
        final int numberOfMessages,
        final int length,
        final long timestamp)
    {
        int count = 0;
        for (int i = 0; i < numberOfMessages; i++)
        {
            offerBuffer.putLong(0, timestamp, LITTLE_ENDIAN);
            final long result = publication.offer(offerBuffer, 0, length);
            if (result < 0)
            {
                checkPublicationResult(result);
                break;
            }
            count++;
        }
        return count;
    }

    private static void checkPublicationResult(final long result)
    {
        if (result == CLOSED ||
            result == NOT_CONNECTED ||
            result == MAX_POSITION_EXCEEDED)
        {
            throw new AeronException("Publication error: " + result);
        }
    }
}
