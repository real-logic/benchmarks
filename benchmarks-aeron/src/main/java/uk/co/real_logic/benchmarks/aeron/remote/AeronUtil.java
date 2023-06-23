/*
 * Copyright 2015-2023 Real Logic Limited.
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
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.ArchiveException;
import io.aeron.archive.client.RecordingDescriptorConsumer;
import io.aeron.driver.MediaDriver;
import io.aeron.exceptions.AeronException;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.ErrorHandler;
import org.agrona.LangUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableLong;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.status.CountersReader;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

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
import static org.agrona.Strings.isEmpty;
import static org.agrona.SystemUtil.parseDuration;
import static org.agrona.concurrent.status.CountersReader.NULL_COUNTER_ID;
import static uk.co.real_logic.benchmarks.aeron.remote.ArchivingMediaDriver.launchArchiveWithEmbeddedDriver;
import static uk.co.real_logic.benchmarks.aeron.remote.ArchivingMediaDriver.launchArchiveWithStandaloneDriver;

final class AeronUtil
{
    static final String SNAPSHOT_SIZE_PROP_NAME = "uk.co.real_logic.benchmarks.aeron.remote.cluster.snapshot.size";
    static final long DEFAULT_SNAPSHOT_SIZE = 0;
    static final String DESTINATION_CHANNELS_PROP_NAME =
        "uk.co.real_logic.benchmarks.aeron.remote.destination.channel";
    static final String DESTINATION_STREAMS_PROP_NAME =
        "uk.co.real_logic.benchmarks.aeron.remote.destination.stream";
    static final String SOURCE_CHANNELS_PROP_NAME = "uk.co.real_logic.benchmarks.aeron.remote.source.channel";
    static final String SOURCE_STREAMS_PROP_NAME = "uk.co.real_logic.benchmarks.aeron.remote.source.stream";
    static final String ARCHIVE_CHANNEL_PROP_NAME = "uk.co.real_logic.benchmarks.aeron.remote.archive.channel";
    static final String ARCHIVE_STREAM_PROP_NAME = "uk.co.real_logic.benchmarks.aeron.remote.archive.stream";
    static final String EMBEDDED_MEDIA_DRIVER_PROP_NAME =
        "uk.co.real_logic.benchmarks.aeron.remote.embedded.media.driver";
    static final String FRAGMENT_LIMIT_PROP_NAME = "uk.co.real_logic.benchmarks.aeron.remote.fragment.limit";
    static final String IDLE_STRATEGY_PROP_NAME = "uk.co.real_logic.benchmarks.aeron.remote.idle.strategy";
    static final String CONNECTION_TIMEOUT_PROP_NAME = "uk.co.real_logic.benchmarks.aeron.remote.connection.timeout";
    static final int FRAGMENT_LIMIT = getInteger(FRAGMENT_LIMIT_PROP_NAME, 10);

    private AeronUtil()
    {
    }

    static long connectionTimeoutNs()
    {
        final String value = getProperty(CONNECTION_TIMEOUT_PROP_NAME);
        if (isEmpty(value))
        {
            return TimeUnit.SECONDS.toNanos(60);
        }

        return parseDuration(CONNECTION_TIMEOUT_PROP_NAME, value);
    }

    static String destinationChannel()
    {
        final String property = getProperty(DESTINATION_CHANNELS_PROP_NAME);
        if (isEmpty(property))
        {
            return "aeron:udp?endpoint=localhost:13333|mtu=1408";
        }

        return property;
    }

    static int destinationStreamId()
    {
        final String property = getProperty(DESTINATION_STREAMS_PROP_NAME);
        if (isEmpty(property))
        {
            return 1_000_000_000;
        }

        return Integer.parseInt(property);
    }

    static String sourceChannel()
    {
        final String property = getProperty(SOURCE_CHANNELS_PROP_NAME);
        if (isEmpty(property))
        {
            return "aeron:udp?endpoint=localhost:13334|mtu=1408";
        }

        return property;
    }

    static int sourceStreamId()
    {
        final String property = getProperty(SOURCE_STREAMS_PROP_NAME);
        if (isEmpty(property))
        {
            return 1_000_000_001;
        }

        return Integer.parseInt(property);
    }

    static String archiveChannel()
    {
        final String property = getProperty(ARCHIVE_CHANNEL_PROP_NAME);
        if (isEmpty(property))
        {
            return IPC_CHANNEL;
        }
        return property;
    }

    static int archiveStream()
    {
        final String property = getProperty(ARCHIVE_STREAM_PROP_NAME);
        if (isEmpty(property))
        {
            return 1_000_100_000;
        }
        return Integer.parseInt(property);
    }

    static boolean embeddedMediaDriver()
    {
        return getBoolean(EMBEDDED_MEDIA_DRIVER_PROP_NAME);
    }

    static IdleStrategy idleStrategy()
    {
        final String idleStrategy = getProperty(IDLE_STRATEGY_PROP_NAME);
        if (isEmpty(idleStrategy))
        {
            return NoOpIdleStrategy.INSTANCE;
        }

        try
        {
            return (IdleStrategy)forName(idleStrategy).getConstructor().newInstance();
        }
        catch (final ReflectiveOperationException | ClassCastException ex)
        {
            throw new IllegalArgumentException("Invalid IdleStrategy: " + idleStrategy, ex);
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
        return embeddedMediaDriver() ? launchArchiveWithEmbeddedDriver() : launchArchiveWithStandaloneDriver();
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
        while (true)
        {
            final int fragmentsRead = image.poll(dataHandler, FRAGMENT_LIMIT);
            if (0 == fragmentsRead)
            {
                if (!running.get() || image.isClosed())
                {
                    break;
                }
            }

            idleStrategy.idle(fragmentsRead);
        }
    }

    static int sendMessages(
        final ExclusivePublication publication,
        final BufferClaim bufferClaim,
        final int numberOfMessages,
        final int messageLength,
        final long timestamp,
        final long checksum)
    {
        int count = 0;
        for (int i = 0; i < numberOfMessages; i++)
        {
            final long result = publication.tryClaim(messageLength, bufferClaim);
            if (result < 0)
            {
                checkPublicationResult(result);
                break;
            }
            final MutableDirectBuffer buffer = bufferClaim.buffer();
            final int offset = bufferClaim.offset();
            buffer.putLong(offset, timestamp, LITTLE_ENDIAN);
            buffer.putLong(offset + messageLength - SIZE_OF_LONG, checksum, LITTLE_ENDIAN);
            bufferClaim.commit();
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

    static void awaitConnected(final BooleanSupplier connection, final long connectionTimeoutNs, final NanoClock clock)
    {
        final long deadlineNs = clock.nanoTime() + connectionTimeoutNs;
        while (!connection.getAsBoolean())
        {
            if (clock.nanoTime() < deadlineNs)
            {
                yieldUninterruptedly();
            }
            else
            {
                throw new IllegalStateException("Failed to connect within timeout of " + connectionTimeoutNs + "ns");
            }
        }
    }

    static void checkPublicationResult(final long result)
    {
        if (result == CLOSED ||
            result == NOT_CONNECTED ||
            result == MAX_POSITION_EXCEEDED)
        {
            throw new AeronException("Publication error: " + result);
        }
    }

    static ErrorHandler rethrowingErrorHandler(final String context)
    {
        return (Throwable throwable) ->
        {
            System.err.println(context);
            throwable.printStackTrace(System.err);
            LangUtil.rethrowUnchecked(throwable);
        };
    }
}
