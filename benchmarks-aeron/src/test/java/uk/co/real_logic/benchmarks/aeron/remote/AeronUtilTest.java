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

import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.junit.jupiter.api.Test;

import static io.aeron.CommonContext.IPC_CHANNEL;
import static java.lang.String.valueOf;
import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static org.junit.jupiter.api.Assertions.*;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

class AeronUtilTest
{
    @Test
    void defaultConfigurationValues()
    {
        assertEquals("aeron:udp?endpoint=localhost:13333", sendChannel());
        assertEquals(1_000_000_000, sendStreamId());
        assertEquals("aeron:udp?endpoint=localhost:13334", receiveChannel());
        assertEquals(1_000_000_001, receiveStreamId());
        assertEquals(IPC_CHANNEL, archiveChannel());
        assertEquals(1_000_000_002, archiveStreamId());
        assertFalse(embeddedMediaDriver());
        assertEquals(10, frameCountLimit());
        assertSame(NoOpIdleStrategy.INSTANCE, idleStrategy());
    }

    @Test
    void explicitConfigurationValues()
    {
        final String senderChannel = "sender";
        final int senderStreamId = Integer.MIN_VALUE;
        final String receiverChannel = "receiver";
        final int receiverStreamId = Integer.MAX_VALUE;
        final String archiveChannel = "archive";
        final int archiveStreamId = 777;
        final boolean embeddedMediaDriver = true;
        final int frameCountLimit = 111;

        setProperty(SEND_CHANNEL_PROP_NAME, senderChannel);
        setProperty(SEND_STREAM_ID_PROP_NAME, valueOf(senderStreamId));
        setProperty(RECEIVE_CHANNEL_PROP_NAME, receiverChannel);
        setProperty(RECEIVE_STREAM_ID_PROP_NAME, valueOf(receiverStreamId));
        setProperty(ARCHIVE_CHANNEL_PROP_NAME, archiveChannel);
        setProperty(ARCHIVE_STREAM_ID_PROP_NAME, valueOf(archiveStreamId));
        setProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME, valueOf(embeddedMediaDriver));
        setProperty(FRAME_COUNT_LIMIT_PROP_NAME, valueOf(frameCountLimit));
        setProperty(IDLE_STRATEGY, YieldingIdleStrategy.class.getName());

        try
        {
            assertEquals(senderChannel, sendChannel());
            assertEquals(senderStreamId, sendStreamId());
            assertEquals(receiverChannel, receiveChannel());
            assertEquals(receiverStreamId, receiveStreamId());
            assertEquals(embeddedMediaDriver, embeddedMediaDriver());
            assertEquals(frameCountLimit, frameCountLimit());
            assertEquals(YieldingIdleStrategy.class, idleStrategy().getClass());
        }
        finally
        {
            clearProperty(SEND_CHANNEL_PROP_NAME);
            clearProperty(SEND_STREAM_ID_PROP_NAME);
            clearProperty(RECEIVE_CHANNEL_PROP_NAME);
            clearProperty(RECEIVE_STREAM_ID_PROP_NAME);
            clearProperty(ARCHIVE_CHANNEL_PROP_NAME);
            clearProperty(ARCHIVE_STREAM_ID_PROP_NAME);
            clearProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME);
            clearProperty(FRAME_COUNT_LIMIT_PROP_NAME);
            clearProperty(IDLE_STRATEGY);
        }
    }
}
