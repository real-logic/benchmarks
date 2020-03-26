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

import io.aeron.Aeron;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.archive.client.AeronArchive;
import io.aeron.driver.MediaDriver;
import org.agrona.LangUtil;

import static java.lang.Class.forName;
import static java.lang.Integer.getInteger;
import static java.lang.System.getProperty;
import static org.agrona.CloseHelper.closeAll;

final class AeronLauncher implements AutoCloseable
{
    static final String SENDER_CHANNEL_PROP_NAME = "aeron.benchmarks.rtt.aeron.sender.channel";
    static final String SENDER_STREAM_ID_PROP_NAME = "aeron.benchmarks.rtt.aeron.sender.streamId";
    static final String RECEIVER_CHANNEL_PROP_NAME = "aeron.benchmarks.rtt.aeron.receiver.channel";
    static final String RECEIVER_STREAM_ID_PROP_NAME = "aeron.benchmarks.rtt.aeron.receiver.streamId";
    static final String MEDIA_DRIVER_PROP_NAME = "aeron.benchmarks.rtt.aeron.mediaDriver";
    static final String FRAME_COUNT_LIMIT_PROP_NAME = "aeron.benchmarks.rtt.aeron.frameCountLimit";

    private final AutoCloseable mediaDriver;
    private final Aeron aeron;
    private final AeronArchive aeronArchive;

    AeronLauncher()
    {
        this(mediaDriverClass());
    }

    AeronLauncher(final Class<? extends AutoCloseable> mediaDriverClass)
    {
        if (null != mediaDriverClass)
        {
            final MediaDriver.Context context = new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .spiesSimulateConnection(true);
            if (MediaDriver.class.equals(mediaDriverClass))
            {
                mediaDriver = MediaDriver.launch(context);
                aeron = Aeron.connect();
                aeronArchive = null;
            }
            else if (ArchivingMediaDriver.class.equals(mediaDriverClass))
            {
                mediaDriver = ArchivingMediaDriver.launch(
                    context,
                    new Archive.Context().recordingEventsEnabled(false));

                aeron = Aeron.connect();

                aeronArchive = AeronArchive.connect(
                    new AeronArchive.Context()
                        .aeron(aeron));
            }
            else
            {
                throw new IllegalArgumentException("Unknown MediaDriver option: " + mediaDriverClass.getName());
            }
        }
        else
        {
            mediaDriver = null;
            aeron = Aeron.connect();
            aeronArchive = null;
        }
    }

    AeronLauncher(final AutoCloseable mediaDriver, final Aeron aeron, final AeronArchive aeronArchive)
    {
        this.mediaDriver = mediaDriver;
        this.aeron = aeron;
        this.aeronArchive = aeronArchive;
    }

    Aeron aeron()
    {
        return aeron;
    }

    AeronArchive aeronArchive()
    {
        return aeronArchive;
    }

    public void close()
    {
        closeAll(aeronArchive, aeron, mediaDriver);
    }

    static String senderChannel()
    {
        return getProperty(SENDER_CHANNEL_PROP_NAME, "aeron:udp?endpoint=localhost:33333");
    }

    static int senderStreamId()
    {
        return getInteger(SENDER_STREAM_ID_PROP_NAME, 101010);
    }

    static String receiverChannel()
    {
        return getProperty(RECEIVER_CHANNEL_PROP_NAME, "aeron:udp?endpoint=localhost:33334");
    }

    static int receiverStreamId()
    {
        return getInteger(RECEIVER_STREAM_ID_PROP_NAME, 101011);
    }

    static Class<? extends AutoCloseable> mediaDriverClass()
    {
        final String className = getProperty(MEDIA_DRIVER_PROP_NAME);
        if (null != className)
        {
            try
            {
                return forName(className).asSubclass(AutoCloseable.class);
            }
            catch (final ClassNotFoundException ex)
            {
                LangUtil.rethrowUnchecked(ex);
            }
        }
        return null;
    }

    static int frameCountLimit()
    {
        return getInteger(FRAME_COUNT_LIMIT_PROP_NAME, 10);
    }
}
