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
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Subscription;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.ArchiveException;
import io.aeron.archive.client.RecordingDescriptorConsumer;
import io.aeron.driver.MediaDriver;
import io.aeron.exceptions.AeronException;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.collections.MutableLong;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.CountersReader;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.CommonContext.IPC_CHANNEL;
import static io.aeron.Publication.*;
import static io.aeron.archive.status.RecordingPos.findCounterIdBySession;
import static io.aeron.archive.status.RecordingPos.getRecordingId;
import static java.lang.Boolean.getBoolean;
import static java.lang.Class.forName;
import static java.lang.Integer.getInteger;
import static java.lang.Long.MAX_VALUE;
import static java.lang.System.getProperty;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.concurrent.status.CountersReader.NULL_COUNTER_ID;

final class AeronUtil
{
    static final String SEND_CHANNEL_PROP_NAME = "uk.co.real_logic.benchmarks.aeron.remote.send.channel";
    static final String SEND_STREAM_ID_PROP_NAME = "uk.co.real_logic.benchmarks.aeron.remote.send.streamId";
    static final String RECEIVE_CHANNEL_PROP_NAME = "uk.co.real_logic.benchmarks.aeron.remote.receive.channel";
    static final String RECEIVE_STREAM_ID_PROP_NAME = "uk.co.real_logic.benchmarks.aeron.remote.receive.streamId";
    static final String ARCHIVE_CHANNEL_PROP_NAME = "uk.co.real_logic.benchmarks.aeron.remote.archive.channel";
    static final String ARCHIVE_STREAM_ID_PROP_NAME = "uk.co.real_logic.benchmarks.aeron.remote.archive.streamId";
    static final String EMBEDDED_MEDIA_DRIVER_PROP_NAME =
        "uk.co.real_logic.benchmarks.aeron.remote.embeddedMediaDriver";
    static final String FRAME_COUNT_LIMIT_PROP_NAME = "uk.co.real_logic.benchmarks.aeron.remote.frameCountLimit";
    static final String IDLE_STRATEGY = "uk.co.real_logic.benchmarks.aeron.remote.idleStrategy";

    private AeronUtil()
    {
    }

    static String sendChannel()
    {
        return getProperty(SEND_CHANNEL_PROP_NAME, "aeron:udp?endpoint=localhost:13333");
    }

    static int sendStreamId()
    {
        return getInteger(SEND_STREAM_ID_PROP_NAME, 1_000_000_000);
    }

    static String receiveChannel()
    {
        return getProperty(RECEIVE_CHANNEL_PROP_NAME, "aeron:udp?endpoint=localhost:13334");
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

    static int fragmentLimit()
    {
        return getInteger(FRAME_COUNT_LIMIT_PROP_NAME, 10);
    }

    static IdleStrategy idleStrategy()
    {
        final String idleStrategy = getProperty(IDLE_STRATEGY);
        if (null == idleStrategy)
        {
            return NoOpIdleStrategy.INSTANCE;
        }

        try
        {
            return (IdleStrategy)forName(idleStrategy).getConstructor().newInstance();
        }
        catch (final ReflectiveOperationException | ClassCastException ex)
        {
            throw new IllegalArgumentException("Invalid IdleStrategy value: " + idleStrategy, ex);
        }
    }

    static MediaDriver launchEmbeddedMediaDriverIfConfigured()
    {
        if (embeddedMediaDriver())
        {
            return MediaDriver.launch(new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .spiesSimulateConnection(true));
        }

        return null;
    }

    static ArchivingMediaDriver launchArchivingMediaDriver()
    {
        final MediaDriver.Context driverContext = new MediaDriver.Context()
            .dirDeleteOnStart(true)
            .spiesSimulateConnection(true);

        return ArchivingMediaDriver.launch(
            driverContext,
            new Archive.Context()
                .aeronDirectoryName(driverContext.aeronDirectoryName())
                .deleteArchiveOnStart(true));
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

    static void pipeMessages(
        final Subscription subscription, final ExclusivePublication publication, final AtomicBoolean running)
    {
        final IdleStrategy idleStrategy = idleStrategy();
        final BufferClaim bufferClaim = new BufferClaim();
        final FragmentHandler dataHandler =
            (buffer, offset, length, header) ->
            {
                long result;
                while ((result = publication.tryClaim(length, bufferClaim)) <= 0)
                {
                    checkPublicationResult(result);
                }

                bufferClaim
                    .flags(header.flags())
                    .putBytes(buffer, offset, length)
                    .commit();
            };

        final Image image = subscription.imageAtIndex(0);
        final int frameCountLimit = fragmentLimit();

        while (true)
        {
            final int fragmentsRead = image.poll(dataHandler, frameCountLimit);
            if (0 == fragmentsRead)
            {
                if (image.isClosed() || !running.get())
                {
                    break;
                }
            }

            idleStrategy.idle(fragmentsRead);
        }
    }

    static int sendMessages(
        final ExclusivePublication publication,
        final UnsafeBuffer offerBuffer,
        final int numberOfMessages,
        final int messageLength,
        final long timestamp,
        final long checksum)
    {
        int count = 0;
        for (int i = 0; i < numberOfMessages; i++)
        {
            offerBuffer.putLong(0, timestamp, LITTLE_ENDIAN);
            offerBuffer.putLong(messageLength - SIZE_OF_LONG, checksum, LITTLE_ENDIAN);

            final long result = publication.offer(offerBuffer, 0, messageLength, null);
            if (result < 0)
            {
                checkPublicationResult(result);
                break;
            }
            count++;
        }

        return count;
    }

    static void installSignalHandler(final AtomicBoolean running)
    {
        final SignalHandler terminationHandler = signal -> running.set(false);

        for (final String signalName : ShutdownSignalBarrier.SIGNAL_NAMES)
        {
            Signal.handle(new Signal(signalName), terminationHandler);
        }
    }

    static void yieldUninterruptedly()
    {
        Thread.yield();
        if (Thread.currentThread().isInterrupted())
        {
            throw new IllegalStateException("Interrupted while yielding...");
        }
    }

    static long replayFullRecording(
        final AeronArchive aeronArchive, final long recordingId, final String replayChannel, final int replayStreamId)
    {
        while (true)
        {
            try
            {
                return aeronArchive.startReplay(recordingId, 0, MAX_VALUE, replayChannel, replayStreamId);
            }
            catch (final ArchiveException ignore)
            {
                yieldUninterruptedly();
            }
        }
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
