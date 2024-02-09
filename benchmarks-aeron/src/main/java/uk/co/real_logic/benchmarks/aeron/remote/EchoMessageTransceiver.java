/*
 * Copyright 2015-2024 Real Logic Limited.
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
import io.aeron.FragmentAssembler;
import io.aeron.Image;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.protocol.HeaderFlyweight;
import org.HdrHistogram.ValueRecorder;
import org.agrona.BitUtil;
import org.agrona.BufferUtil;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.SystemNanoClock;
import org.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.benchmarks.remote.Configuration;
import uk.co.real_logic.benchmarks.remote.MessageTransceiver;

import java.nio.file.Path;

import static io.aeron.Aeron.connect;
import static io.aeron.logbuffer.FrameDescriptor.FRAME_ALIGNMENT;
import static io.aeron.protocol.DataHeaderFlyweight.*;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

public final class EchoMessageTransceiver extends MessageTransceiver
{
    private static final int MESSAGE_BUFFER_SIZE = 4 * 1024 * 1024;
    private final UnsafeBuffer msgBuffer = new UnsafeBuffer(
        BufferUtil.allocateDirectAligned(MESSAGE_BUFFER_SIZE, FRAME_ALIGNMENT));
    private final FragmentAssembler dataHandler = new FragmentAssembler(
        (buffer, offset, length, header) ->
        {
            final long timestamp = buffer.getLong(offset, LITTLE_ENDIAN);
            final long checksum = buffer.getLong(offset + length - SIZE_OF_LONG, LITTLE_ENDIAN);
            onMessageReceived(timestamp, checksum);
        });

    private final MediaDriver mediaDriver;
    private final Aeron aeron;
    private final boolean ownsAeronClient;
    private Path logsDir;
    ExclusivePublication publication;
    private Subscription subscription;
    private Image image;

    public EchoMessageTransceiver(final NanoClock nanoClock, final ValueRecorder valueRecorder)
    {
        this(nanoClock, valueRecorder, launchEmbeddedMediaDriverIfConfigured(), connect(), true);
    }

    EchoMessageTransceiver(
        final NanoClock nanoClock,
        final ValueRecorder valueRecorder,
        final MediaDriver mediaDriver,
        final Aeron aeron,
        final boolean ownsAeronClient)
    {
        super(nanoClock, valueRecorder);
        this.mediaDriver = mediaDriver;
        this.aeron = aeron;
        this.ownsAeronClient = ownsAeronClient;
    }

    public void init(final Configuration configuration)
    {
        logsDir = configuration.logsDir();
        publication = aeron.addExclusivePublication(destinationChannel(), destinationStreamId());
        subscription = aeron.addSubscription(sourceChannel(), sourceStreamId());

        awaitConnected(
            () -> subscription.isConnected() && publication.isConnected(),
            connectionTimeoutNs(),
            SystemNanoClock.INSTANCE);

        image = subscription.imageAtIndex(0);
    }

    public void destroy()
    {
        final String prefix = "echo-client-";
        AeronUtil.dumpAeronStats(
            aeron.context().cncFile(),
            logsDir.resolve(prefix + "aeron-stat.txt"),
            logsDir.resolve(prefix + "errors.txt"));
        closeAll(subscription, publication);

        if (ownsAeronClient)
        {
            closeAll(aeron, mediaDriver);
        }
    }

    public int send(final int numberOfMessages, final int messageLength, final long timestamp, final long checksum)
    {
        final int frameLength = messageLength + HEADER_LENGTH;
        final int alignedFrameLength = BitUtil.align(frameLength, FRAME_ALIGNMENT);
        final long availableWindow = publication.availableWindow();
        if (availableWindow < alignedFrameLength)
        {
            return 0;
        }

        final int termBufferLength = publication.termBufferLength();
        int termOffset = publication.termOffset();
        int remainingSpace = termBufferLength - termOffset;
        int termId = publication.termId();
        if (remainingSpace < alignedFrameLength)
        {
            termId++;
            termOffset = 0;
            if (remainingSpace >= HEADER_LENGTH)
            {
                final long result = publication.appendPadding(remainingSpace - HEADER_LENGTH);
                if (result < 0)
                {
                    checkPublicationResult(result);
                    return 0;
                }
            }
            remainingSpace = termBufferLength;
        }

        final int limit = Math.min((int)availableWindow, Math.min(remainingSpace, MESSAGE_BUFFER_SIZE));
        int i = 0, offset = 0;
        for (; i < numberOfMessages && offset + alignedFrameLength <= limit; i++)
        {
            // header
            msgBuffer.putInt(offset, frameLength, LITTLE_ENDIAN);
            msgBuffer.putByte(offset + VERSION_FIELD_OFFSET, HeaderFlyweight.CURRENT_VERSION);
            msgBuffer.putByte(offset + FLAGS_FIELD_OFFSET, (byte)BEGIN_AND_END_FLAGS);
            msgBuffer.putShort(offset + TYPE_FIELD_OFFSET, (short)HeaderFlyweight.HDR_TYPE_DATA, LITTLE_ENDIAN);
            msgBuffer.putInt(offset + TERM_OFFSET_FIELD_OFFSET, termOffset + offset, LITTLE_ENDIAN);
            msgBuffer.putInt(offset + SESSION_ID_FIELD_OFFSET, publication.sessionId(), LITTLE_ENDIAN);
            msgBuffer.putInt(offset + STREAM_ID_FIELD_OFFSET, publication.streamId(), LITTLE_ENDIAN);
            msgBuffer.putInt(offset + TERM_ID_FIELD_OFFSET, termId, LITTLE_ENDIAN);
            msgBuffer.putLong(offset + RESERVED_VALUE_OFFSET, DEFAULT_RESERVE_VALUE, LITTLE_ENDIAN);

            // body
            msgBuffer.putLong(offset + HEADER_LENGTH, timestamp, LITTLE_ENDIAN);
            msgBuffer.putLong(offset + HEADER_LENGTH + messageLength - SIZE_OF_LONG, checksum, LITTLE_ENDIAN);

            offset += alignedFrameLength;
        }

        if (i > 0)
        {
            final long result = publication.offerBlock(msgBuffer, 0, offset);
            if (result < 0)
            {
                checkPublicationResult(result);
                return 0;
            }
        }

        return i;
    }

    public void receive()
    {
        image.poll(dataHandler, FRAGMENT_LIMIT);
    }
}
