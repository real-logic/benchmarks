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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.aeron.CommonContext.IPC_CHANNEL;
import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static org.junit.jupiter.api.Assertions.*;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

class AeronUtilTest
{
    @AfterEach
    void after()
    {
        clearProperty(DESTINATION_CHANNELS_PROP_NAME);
        clearProperty(DESTINATION_STREAMS_PROP_NAME);
        clearProperty(SOURCE_CHANNELS_PROP_NAME);
        clearProperty(SOURCE_STREAMS_PROP_NAME);
        clearProperty(PASSIVE_CHANNELS_PROP_NAME);
        clearProperty(PASSIVE_STREAMS_PROP_NAME);
        clearProperty(ARCHIVE_CHANNEL_PROP_NAME);
        clearProperty(ARCHIVE_STREAM_PROP_NAME);
        clearProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME);
        clearProperty(IDLE_STRATEGY);
        clearProperty(RECONNECT_IF_IMAGE_CLOSED);
    }

    @Test
    void defaultConfigurationValues()
    {
        assertArrayEquals(new String[]{ "aeron:udp?endpoint=localhost:13333" }, destinationChannels());
        assertArrayEquals(new int[]{ 1_000_000_000 }, destinationStreams());
        assertArrayEquals(new String[]{ "aeron:udp?endpoint=localhost:13334" }, sourceChannels());
        assertArrayEquals(new int[]{ 1_000_000_001 }, sourceStreams());
        assertArrayEquals(new String[0], passiveChannels());
        assertArrayEquals(new int[0], passiveStreams());
        assertEquals(TimeUnit.SECONDS.toNanos(1), passiveChannelsKeepAliveIntervalNanos());
        assertEquals(1000, passiveChannelsPollFrequency());
        assertEquals(IPC_CHANNEL, archiveChannel());
        assertEquals(1_000_100_000, archiveStream());
        assertFalse(embeddedMediaDriver());
        assertSame(NoOpIdleStrategy.INSTANCE, idleStrategy());
        assertFalse(reconnectIfImageClosed());
    }

    @Test
    void defaultConfigurationValuesShouldBeUsedIfEmptyValuesAreSet()
    {
        setProperty(DESTINATION_CHANNELS_PROP_NAME, "");
        setProperty(DESTINATION_STREAMS_PROP_NAME, "");
        setProperty(SOURCE_CHANNELS_PROP_NAME, "");
        setProperty(SOURCE_STREAMS_PROP_NAME, "");
        setProperty(PASSIVE_CHANNELS_PROP_NAME, "");
        setProperty(PASSIVE_STREAMS_PROP_NAME, "");
        setProperty(PASSIVE_CHANNELS_KEEP_ALIVE_INTERVAL_PROP_NAME, "");
        setProperty(PASSIVE_CHANNELS_POLL_FREQUENCY_PROP_NAME, "");
        setProperty(ARCHIVE_CHANNEL_PROP_NAME, "");
        setProperty(ARCHIVE_STREAM_PROP_NAME, "");
        setProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME, "");
        setProperty(IDLE_STRATEGY, "");
        setProperty(RECONNECT_IF_IMAGE_CLOSED, "");

        assertArrayEquals(new String[]{ "aeron:udp?endpoint=localhost:13333" }, destinationChannels());
        assertArrayEquals(new int[]{ 1_000_000_000 }, destinationStreams());
        assertArrayEquals(new String[]{ "aeron:udp?endpoint=localhost:13334" }, sourceChannels());
        assertArrayEquals(new int[]{ 1_000_000_001 }, sourceStreams());
        assertArrayEquals(new String[0], passiveChannels());
        assertArrayEquals(new int[0], passiveStreams());
        assertEquals(TimeUnit.SECONDS.toNanos(1), passiveChannelsKeepAliveIntervalNanos());
        assertEquals(1000, passiveChannelsPollFrequency());
        assertEquals(IPC_CHANNEL, archiveChannel());
        assertEquals(1_000_100_000, archiveStream());
        assertFalse(embeddedMediaDriver());
        assertSame(NoOpIdleStrategy.INSTANCE, idleStrategy());
        assertFalse(reconnectIfImageClosed());
    }

    @Test
    void explicitConfigurationValues()
    {
        setProperty(DESTINATION_CHANNELS_PROP_NAME, "ch1:5001,ch2:5002,ch3:5003");
        setProperty(DESTINATION_STREAMS_PROP_NAME, "100,101,102,");
        setProperty(SOURCE_CHANNELS_PROP_NAME, "ch1:8001,ch2:8002,ch3:8003");
        setProperty(SOURCE_STREAMS_PROP_NAME, "200,201,202,");
        setProperty(PASSIVE_CHANNELS_PROP_NAME, "ch4:4444,ch7:7777");
        setProperty(PASSIVE_STREAMS_PROP_NAME, "1,2");
        setProperty(PASSIVE_CHANNELS_KEEP_ALIVE_INTERVAL_PROP_NAME, "125us");
        setProperty(PASSIVE_CHANNELS_POLL_FREQUENCY_PROP_NAME, "22");
        setProperty(ARCHIVE_CHANNEL_PROP_NAME, "localhost");
        setProperty(ARCHIVE_STREAM_PROP_NAME, "777");
        setProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME, "true");
        setProperty(IDLE_STRATEGY, YieldingIdleStrategy.class.getName());
        setProperty(RECONNECT_IF_IMAGE_CLOSED, "true");

        assertArrayEquals(new String[]{ "ch1:5001", "ch2:5002", "ch3:5003" }, destinationChannels());
        assertArrayEquals(new int[]{ 100, 101, 102 }, destinationStreams());
        assertArrayEquals(new String[]{ "ch1:8001", "ch2:8002", "ch3:8003" }, sourceChannels());
        assertArrayEquals(new int[]{ 200, 201, 202 }, sourceStreams());
        assertArrayEquals(new String[]{ "ch4:4444", "ch7:7777" }, passiveChannels());
        assertArrayEquals(new int[]{ 1, 2 }, passiveStreams());
        assertEquals("localhost", archiveChannel());
        assertEquals(777, archiveStream());
        assertEquals(TimeUnit.MICROSECONDS.toNanos(125), passiveChannelsKeepAliveIntervalNanos());
        assertEquals(22, passiveChannelsPollFrequency());
        assertTrue(embeddedMediaDriver());
        assertEquals(YieldingIdleStrategy.class, idleStrategy().getClass());
        assertTrue(reconnectIfImageClosed());
    }

    @Test
    void testAssertChannelsAndStreamsMatch()
    {
        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> assertChannelsAndStreamsMatch(new String[]{ "a" }, new int[]{ 1, 2 }, "channels", "streams"));

        assertEquals("Number of 'channels' does not match with 'streams':\n [a]\n [1, 2]", exception.getMessage());
    }
}
