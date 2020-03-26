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
import io.aeron.Publication;
import io.aeron.archive.client.AeronArchive;
import org.junit.jupiter.api.Test;

import static java.lang.String.valueOf;
import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronLauncher.*;

class AeronLauncherTest
{
    @Test
    void defaultConfigurationValues()
    {
        assertEquals("aeron:udp?endpoint=localhost:33333", senderChannel());
        assertEquals(101010, senderStreamId());
        assertEquals("aeron:udp?endpoint=localhost:33334", receiverChannel());
        assertEquals(101011, receiverStreamId());
        assertNull(mediaDriverClass());
        assertEquals(10, frameCountLimit());
    }

    @Test
    void explicitConfigurationValues()
    {
        final String senderChannel = "sender";
        final int senderStreamId = Integer.MIN_VALUE;
        final String receiverChannel = "receiver";
        final int receiverStreamId = Integer.MAX_VALUE;
        final Class<Publication> mediaDriver = Publication.class;
        final int frameCountLimit = 111;

        setProperty(SENDER_CHANNEL_PROP_NAME, senderChannel);
        setProperty(SENDER_STREAM_ID_PROP_NAME, valueOf(senderStreamId));
        setProperty(RECEIVER_CHANNEL_PROP_NAME, receiverChannel);
        setProperty(RECEIVER_STREAM_ID_PROP_NAME, valueOf(receiverStreamId));
        setProperty(MEDIA_DRIVER_PROP_NAME, mediaDriver.getName());
        setProperty(FRAME_COUNT_LIMIT_PROP_NAME, valueOf(frameCountLimit));
        try
        {
            assertEquals(senderChannel, senderChannel());
            assertEquals(senderStreamId, senderStreamId());
            assertEquals(receiverChannel, receiverChannel());
            assertEquals(receiverStreamId, receiverStreamId());
            assertEquals(mediaDriver, mediaDriverClass());
            assertEquals(frameCountLimit, frameCountLimit());
        }
        finally
        {
            clearProperty(SENDER_CHANNEL_PROP_NAME);
            clearProperty(SENDER_STREAM_ID_PROP_NAME);
            clearProperty(RECEIVER_CHANNEL_PROP_NAME);
            clearProperty(RECEIVER_STREAM_ID_PROP_NAME);
            clearProperty(MEDIA_DRIVER_PROP_NAME);
            clearProperty(FRAME_COUNT_LIMIT_PROP_NAME);
        }
    }

    @Test
    void mediaDriveClassThrowsClassNotFoundExceptionIfUnknownClassSpecified()
    {
        final String className = "garbage class";
        setProperty(MEDIA_DRIVER_PROP_NAME, className);
        try
        {
            final ClassNotFoundException exception =
                assertThrows(ClassNotFoundException.class, AeronLauncher::mediaDriverClass);

            assertEquals(className, exception.getMessage());
        }
        finally
        {
            clearProperty(MEDIA_DRIVER_PROP_NAME);
        }
    }

    @Test
    void mediaDriveClassThrowsClassCastExceptionIfClassDoesNotImplementAutoCloseableInterface()
    {
        setProperty(MEDIA_DRIVER_PROP_NAME, Integer.class.getName());
        try
        {
            final ClassCastException exception =
                assertThrows(ClassCastException.class, AeronLauncher::mediaDriverClass);

            assertEquals("class java.lang.Integer", exception.getMessage());
        }
        finally
        {
            clearProperty(MEDIA_DRIVER_PROP_NAME);
        }
    }

    @Test
    void closeAllResources() throws Exception
    {
        final AssertionError driverCloseError = new AssertionError("driver close error");
        final AutoCloseable mediaDriver =
            mockThrowingCloseable(AutoCloseable.class, driverCloseError);

        final UnsupportedOperationException aeronCloseError = new UnsupportedOperationException("aeron close error");
        final Aeron aeron = mockThrowingCloseable(Aeron.class, aeronCloseError);

        final IllegalArgumentException aeronArchiveCloseError =
            new IllegalArgumentException("aeron archive close error");
        final AeronArchive aeronArchive =
            mockThrowingCloseable(AeronArchive.class, aeronArchiveCloseError);

        final AeronLauncher launcher = new AeronLauncher(mediaDriver, aeron, aeronArchive);

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, launcher::close);

        assertSame(aeronArchiveCloseError, exception);
        assertSame(aeronCloseError, exception.getSuppressed()[0]);
        assertSame(driverCloseError, exception.getSuppressed()[1]);
    }

    @Test
    void throwsIllegalArgumentExceptionOnUnknownMediaDriverClass()
    {
        final IllegalArgumentException exception =
            assertThrows(IllegalArgumentException.class, () -> new AeronLauncher(AutoCloseable.class));

        assertEquals("Unknown MediaDriver option: java.lang.AutoCloseable", exception.getMessage());
    }

    private <T extends AutoCloseable> T mockThrowingCloseable(
        final Class<T> klass, final Throwable exception) throws Exception
    {
        final T mock = mock(klass);
        doThrow(exception).when(mock).close();
        return mock;
    }
}