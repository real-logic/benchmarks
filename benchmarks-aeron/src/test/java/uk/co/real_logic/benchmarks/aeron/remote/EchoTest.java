/*
 * Copyright 2015-2022 Real Logic Limited.
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
import io.aeron.driver.MediaDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.Aeron.connect;
import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

class EchoTest extends AbstractTest<MediaDriver, Aeron, EchoMessageTransceiver, EchoNode>
{
    protected EchoNode createNode(final AtomicBoolean running, final MediaDriver mediaDriver, final Aeron aeron)
    {
        return new EchoNode(running, mediaDriver, aeron, false);
    }

    protected MediaDriver createDriver()
    {
        return launchEmbeddedMediaDriverIfConfigured();
    }

    protected Aeron connectToDriver()
    {
        return connect();
    }

    protected Class<EchoMessageTransceiver> messageTransceiverClass()
    {
        return EchoMessageTransceiver.class;
    }

    protected EchoMessageTransceiver createMessageTransceiver(final MediaDriver mediaDriver, final Aeron aeron)
    {
        return new EchoMessageTransceiver(mediaDriver, aeron, false);
    }

    @AfterEach
    void after()
    {
        super.after();
        clearProperty(DESTINATION_CHANNELS_PROP_NAME);
        clearProperty(DESTINATION_STREAMS_PROP_NAME);
        clearProperty(SOURCE_CHANNELS_PROP_NAME);
        clearProperty(SOURCE_STREAMS_PROP_NAME);
    }

    @Timeout(30)
    @Test
    void multipleStreams(final @TempDir Path tempDir) throws Exception
    {
        setProperty(
            DESTINATION_CHANNELS_PROP_NAME,
            "aeron:udp?endpoint=localhost:13100,aeron:udp?endpoint=localhost:13101,aeron:udp?endpoint=localhost:13102");
        setProperty(DESTINATION_STREAMS_PROP_NAME, "13100,13101,13102");
        setProperty(
            SOURCE_CHANNELS_PROP_NAME,
            "aeron:udp?endpoint=localhost:13200,aeron:udp?endpoint=localhost:13201,aeron:udp?endpoint=localhost:13202");
        setProperty(SOURCE_STREAMS_PROP_NAME, "13200,13201,13202");

        test(5000, 32, 1, tempDir);
    }

    @Timeout(30)
    @Test
    void failIfNumberOfDestinationChannelsDoesNotMatchSourceChannels(final @TempDir Path tempDir)
    {
        setProperty(
            DESTINATION_CHANNELS_PROP_NAME,
            "aeron:udp?endpoint=localhost:13100,aeron:udp?endpoint=localhost:13101");
        setProperty(DESTINATION_STREAMS_PROP_NAME, "13100,13101");
        setProperty(SOURCE_CHANNELS_PROP_NAME, "aeron:udp?endpoint=localhost:13200");
        setProperty(SOURCE_STREAMS_PROP_NAME, "13200");

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> test(1000, 32, 1, tempDir));

        assertEquals("Number of destinations does not match the number of sources:\n " +
            "[aeron:udp?endpoint=localhost:13100, aeron:udp?endpoint=localhost:13101]\n " +
            "[aeron:udp?endpoint=localhost:13200]",
            exception.getMessage());
    }
}
