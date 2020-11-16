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

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import uk.co.real_logic.benchmarks.remote.MessageRecorder;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.Aeron.connect;
import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
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

    protected EchoMessageTransceiver createMessageTransceiver(
        final MediaDriver mediaDriver, final Aeron aeron, final MessageRecorder messageRecorder)
    {
        return new EchoMessageTransceiver(mediaDriver, aeron, false, messageRecorder);
    }

    @AfterEach
    void after()
    {
        super.after();
        clearProperty(PASSIVE_CHANNELS_PROP_NAME);
        clearProperty(PASSIVE_STREAMS_PROP_NAME);
        clearProperty(PASSIVE_CHANNELS_KEEP_ALIVE_INTERVAL_PROP_NAME);
        clearProperty(PASSIVE_CHANNELS_POLL_FREQUENCY_PROP_NAME);
    }

    @Timeout(30)
    @Test
    void passiveChannels(final @TempDir Path tempDir) throws Exception
    {
        setProperty(
            PASSIVE_CHANNELS_PROP_NAME, "aeron:udp?endpoint=localhost:13000,aeron:udp?endpoint=localhost:13001");
        setProperty(PASSIVE_STREAMS_PROP_NAME, "13000,13001");
        setProperty(PASSIVE_CHANNELS_KEEP_ALIVE_INTERVAL_PROP_NAME, "500ms");
        setProperty(PASSIVE_CHANNELS_POLL_FREQUENCY_PROP_NAME, "70");

        test(1000, 32, 1, tempDir);
    }

}
