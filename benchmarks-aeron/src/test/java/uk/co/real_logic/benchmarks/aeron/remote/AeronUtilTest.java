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

import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

import static io.aeron.CommonContext.IPC_CHANNEL;
import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static java.util.concurrent.TimeUnit.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
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
        clearProperty(ARCHIVE_CHANNEL_PROP_NAME);
        clearProperty(ARCHIVE_STREAM_PROP_NAME);
        clearProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME);
        clearProperty(IDLE_STRATEGY_PROP_NAME);
    }

    @Test
    void defaultConfigurationValues()
    {
        assertEquals("aeron:udp?endpoint=localhost:13333|mtu=1408", destinationChannel());
        assertEquals(1_000_000_000, destinationStreamId());
        assertEquals("aeron:udp?endpoint=localhost:13334|mtu=1408", sourceChannel());
        assertEquals(1_000_000_001, sourceStreamId());
        assertEquals(IPC_CHANNEL, archiveChannel());
        assertEquals(1_000_100_000, archiveStream());
        assertFalse(embeddedMediaDriver());
        assertSame(NoOpIdleStrategy.INSTANCE, idleStrategy());
    }

    @Test
    void defaultConfigurationValuesShouldBeUsedIfEmptyValuesAreSet()
    {
        setProperty(DESTINATION_CHANNELS_PROP_NAME, "");
        setProperty(DESTINATION_STREAMS_PROP_NAME, "");
        setProperty(SOURCE_CHANNELS_PROP_NAME, "");
        setProperty(SOURCE_STREAMS_PROP_NAME, "");
        setProperty(ARCHIVE_CHANNEL_PROP_NAME, "");
        setProperty(ARCHIVE_STREAM_PROP_NAME, "");
        setProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME, "");
        setProperty(IDLE_STRATEGY_PROP_NAME, "");

        assertEquals("aeron:udp?endpoint=localhost:13333|mtu=1408", destinationChannel());
        assertEquals(1_000_000_000, destinationStreamId());
        assertEquals("aeron:udp?endpoint=localhost:13334|mtu=1408", sourceChannel());
        assertEquals(1_000_000_001, sourceStreamId());
        assertEquals(IPC_CHANNEL, archiveChannel());
        assertEquals(1_000_100_000, archiveStream());
        assertFalse(embeddedMediaDriver());
        assertSame(NoOpIdleStrategy.INSTANCE, idleStrategy());
    }

    @Test
    void explicitConfigurationValues()
    {
        setProperty(DESTINATION_CHANNELS_PROP_NAME, "ch1:5001,ch2:5002,ch3:5003");
        setProperty(DESTINATION_STREAMS_PROP_NAME, "100");
        setProperty(SOURCE_CHANNELS_PROP_NAME, "ch1:8001,ch2:8002,ch3:8003");
        setProperty(SOURCE_STREAMS_PROP_NAME, "200");
        setProperty(ARCHIVE_CHANNEL_PROP_NAME, "localhost");
        setProperty(ARCHIVE_STREAM_PROP_NAME, "777");
        setProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME, "true");
        setProperty(IDLE_STRATEGY_PROP_NAME, YieldingIdleStrategy.class.getName());

        assertEquals("ch1:5001,ch2:5002,ch3:5003", destinationChannel());
        assertEquals(100, destinationStreamId());
        assertEquals("ch1:8001,ch2:8002,ch3:8003", sourceChannel());
        assertEquals(200, sourceStreamId());
        assertEquals("localhost", archiveChannel());
        assertEquals(777, archiveStream());
        assertTrue(embeddedMediaDriver());
        assertEquals(YieldingIdleStrategy.class, idleStrategy().getClass());
    }

    @Test
    void connectionTimeoutNsIsSixtySecondsByDefault()
    {
        System.setProperty(CONNECTION_TIMEOUT_PROP_NAME, "");
        try
        {
            assertEquals(SECONDS.toNanos(60), connectionTimeoutNs());
        }
        finally
        {
            System.clearProperty(CONNECTION_TIMEOUT_PROP_NAME);
        }
    }

    @ParameterizedTest
    @MethodSource("connectionTimeouts")
    void connectionTimeoutNsReturnsUserSpecifiedValue(final String connectionTimeout, final long expectedValueNs)
    {
        System.setProperty(CONNECTION_TIMEOUT_PROP_NAME, connectionTimeout);
        try
        {
            assertEquals(expectedValueNs, connectionTimeoutNs());
        }
        finally
        {
            System.clearProperty(CONNECTION_TIMEOUT_PROP_NAME);
        }
    }

    @Test
    void awaitConnectedReturnsImmediatelyIfAlreadyConnected()
    {
        final BooleanSupplier connection = mock(BooleanSupplier.class);
        when(connection.getAsBoolean()).thenReturn(true);
        final long connectionTimeoutNs = 0;
        final NanoClock clock = mock(NanoClock.class);

        awaitConnected(connection, connectionTimeoutNs, clock);

        final InOrder inOrder = inOrder(clock, connection);
        inOrder.verify(clock).nanoTime();
        inOrder.verify(connection).getAsBoolean();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void awaitConnectedShouldYieldUntilConnected()
    {
        final BooleanSupplier connection = mock(BooleanSupplier.class);
        when(connection.getAsBoolean()).thenReturn(false, false, true);
        final long connectionTimeoutNs = 10;
        final NanoClock clock = mock(NanoClock.class);
        when(clock.nanoTime()).thenReturn(0L, 3L, 8L, 15L);

        awaitConnected(connection, connectionTimeoutNs, clock);

        verify(connection, times(3)).getAsBoolean();
        verify(clock, times(3)).nanoTime();
        verifyNoMoreInteractions(clock, connection);
    }

    @Test
    void awaitConnectedShouldThrowIfNotConnectedUntilTimeout()
    {
        final BooleanSupplier connection = mock(BooleanSupplier.class);
        final long connectionTimeoutNs = 8;
        final NanoClock clock = mock(NanoClock.class);
        when(clock.nanoTime()).thenReturn(Long.MAX_VALUE);

        final IllegalStateException exception =
            assertThrows(IllegalStateException.class, () -> awaitConnected(connection, connectionTimeoutNs, clock));
        assertEquals("Failed to connect within timeout of 8ns", exception.getMessage());
    }

    private static List<Arguments> connectionTimeouts()
    {
        return Arrays.asList(
            Arguments.arguments("5ns", 5L),
            Arguments.arguments("16us", MICROSECONDS.toNanos(16)),
            Arguments.arguments("31ms", MILLISECONDS.toNanos(31)),
            Arguments.arguments("42s", SECONDS.toNanos(42)));
    }
}
