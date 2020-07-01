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
        assertEquals("aeron:udp?endpoint=localhost:13333", destinationChannel());
        assertEquals(1_000_000_000, destinationStreamId());
        assertEquals("aeron:udp?endpoint=localhost:13334", sourceChannel());
        assertEquals(1_000_000_001, sourceStreamId());
        assertEquals(IPC_CHANNEL, archiveChannel());
        assertEquals(1_000_000_002, archiveStreamId());
        assertFalse(embeddedMediaDriver());
        assertEquals(10, fragmentLimit());
        assertSame(NoOpIdleStrategy.INSTANCE, idleStrategy());
        assertFalse(reconnectIfImageClosed());
    }

    @Test
    void explicitConfigurationValues()
    {
        final String destChannel = "sender";
        final int destStreamId = Integer.MIN_VALUE;
        final String srcChannel = "receiver";
        final int srcStreamId = Integer.MAX_VALUE;
        final String archiveChannel = "archive";
        final int archiveStreamId = 777;
        final boolean embeddedMediaDriver = true;
        final int fragmentLimit = 111;

        setProperty(DESTINATION_CHANNEL_PROP_NAME, destChannel);
        setProperty(DESTINATION_STREAM_ID_PROP_NAME, valueOf(destStreamId));
        setProperty(SOURCE_CHANNEL_PROP_NAME, srcChannel);
        setProperty(SOURCE_STREAM_ID_PROP_NAME, valueOf(srcStreamId));
        setProperty(ARCHIVE_CHANNEL_PROP_NAME, archiveChannel);
        setProperty(ARCHIVE_STREAM_ID_PROP_NAME, valueOf(archiveStreamId));
        setProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME, valueOf(embeddedMediaDriver));
        setProperty(FRAGMENT_LIMIT_PROP_NAME, valueOf(fragmentLimit));
        setProperty(IDLE_STRATEGY, YieldingIdleStrategy.class.getName());
        setProperty(RECONNECT_IF_IMAGE_CLOSED, "true");

        try
        {
            assertEquals(destChannel, destinationChannel());
            assertEquals(destStreamId, destinationStreamId());
            assertEquals(srcChannel, sourceChannel());
            assertEquals(srcStreamId, sourceStreamId());
            assertEquals(embeddedMediaDriver, embeddedMediaDriver());
            assertEquals(fragmentLimit, fragmentLimit());
            assertEquals(YieldingIdleStrategy.class, idleStrategy().getClass());
            assertTrue(reconnectIfImageClosed());
        }
        finally
        {
            clearProperty(DESTINATION_CHANNEL_PROP_NAME);
            clearProperty(DESTINATION_STREAM_ID_PROP_NAME);
            clearProperty(SOURCE_CHANNEL_PROP_NAME);
            clearProperty(SOURCE_STREAM_ID_PROP_NAME);
            clearProperty(ARCHIVE_CHANNEL_PROP_NAME);
            clearProperty(ARCHIVE_STREAM_ID_PROP_NAME);
            clearProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME);
            clearProperty(FRAGMENT_LIMIT_PROP_NAME);
            clearProperty(IDLE_STRATEGY);
            clearProperty(RECONNECT_IF_IMAGE_CLOSED);
        }
    }
}
