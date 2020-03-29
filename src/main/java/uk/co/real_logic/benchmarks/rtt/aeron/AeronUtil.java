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

import io.aeron.archive.Archive;
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.driver.MediaDriver;
import io.aeron.exceptions.AeronException;

import static io.aeron.CommonContext.IPC_CHANNEL;
import static io.aeron.Publication.*;
import static java.lang.Boolean.getBoolean;
import static java.lang.Integer.getInteger;
import static java.lang.System.getProperty;

final class AeronUtil
{
    static final String SENDER_CHANNEL_PROP_NAME = "aeron.benchmarks.rtt.aeron.sender.channel";
    static final String SENDER_STREAM_ID_PROP_NAME = "aeron.benchmarks.rtt.aeron.sender.streamId";
    static final String RECEIVER_CHANNEL_PROP_NAME = "aeron.benchmarks.rtt.aeron.receiver.channel";
    static final String RECEIVER_STREAM_ID_PROP_NAME = "aeron.benchmarks.rtt.aeron.receiver.streamId";
    static final String ARCHIVE_CHANNEL_PROP_NAME = "aeron.benchmarks.rtt.aeron.archive.channel";
    static final String ARCHIVE_STREAM_ID_PROP_NAME = "aeron.benchmarks.rtt.aeron.archive.streamId";
    static final String EMBEDDED_MEDIA_DRIVER_PROP_NAME = "aeron.benchmarks.rtt.aeron.embeddedMediaDriver";
    static final String FRAME_COUNT_LIMIT_PROP_NAME = "aeron.benchmarks.rtt.aeron.frameCountLimit";

    private AeronUtil()
    {
    }

    static String senderChannel()
    {
        return getProperty(SENDER_CHANNEL_PROP_NAME, "aeron:udp?endpoint=localhost:33333");
    }

    static int senderStreamId()
    {
        return getInteger(SENDER_STREAM_ID_PROP_NAME, 1_000_000_000);
    }

    static String receiverChannel()
    {
        return getProperty(RECEIVER_CHANNEL_PROP_NAME, "aeron:udp?endpoint=localhost:33334");
    }

    static int receiverStreamId()
    {
        return getInteger(RECEIVER_STREAM_ID_PROP_NAME, 1_000_000_001);
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

    static void checkPublicationResult(final long result)
    {
        if (result == CLOSED ||
            result == NOT_CONNECTED ||
            result == MAX_POSITION_EXCEEDED)
        {
            throw new AeronException("Publication error: " + result);
        }
    }
}
