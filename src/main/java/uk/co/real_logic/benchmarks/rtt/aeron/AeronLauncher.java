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
import io.aeron.driver.MediaDriver;

import static java.lang.Boolean.getBoolean;
import static java.lang.Integer.getInteger;
import static java.lang.System.getProperty;
import static org.agrona.CloseHelper.closeAll;

final class AeronLauncher implements AutoCloseable
{
    static final String SENDER_CHANNEL_PROP_NAME = "aeron.benchmarks.rtt.aeron.sender.channel";
    static final String SENDER_STREAM_ID_PROP_NAME = "aeron.benchmarks.rtt.aeron.sender.streamId";
    static final String RECEIVER_CHANNEL_PROP_NAME = "aeron.benchmarks.rtt.aeron.receiver.channel";
    static final String RECEIVER_STREAM_ID_PROP_NAME = "aeron.benchmarks.rtt.aeron.receiver.streamId";
    static final String EMBEDDED_MEDIA_DRIVER_PROP_NAME = "aeron.benchmarks.rtt.aeron.embeddedMediaDriver";
    static final String FRAME_COUNT_LIMIT_PROP_NAME = "aeron.benchmarks.rtt.aeron.frameCountLimit";

    private final MediaDriver mediaDriver;
    private final Aeron aeron;

    AeronLauncher()
    {
        if (embeddedMediaDriver())
        {
            mediaDriver = MediaDriver.launch(new MediaDriver.Context().dirDeleteOnStart(true));
        }
        else
        {
            mediaDriver = null;
        }
        aeron = Aeron.connect();
    }

    MediaDriver mediaDriver()
    {
        return mediaDriver;
    }

    Aeron aeron()
    {
        return aeron;
    }

    public void close()
    {
        closeAll(aeron, mediaDriver);
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

    static boolean embeddedMediaDriver()
    {
        return getBoolean(EMBEDDED_MEDIA_DRIVER_PROP_NAME);
    }

    static int frameCountLimit()
    {
        return getInteger(FRAME_COUNT_LIMIT_PROP_NAME, 10);
    }
}
