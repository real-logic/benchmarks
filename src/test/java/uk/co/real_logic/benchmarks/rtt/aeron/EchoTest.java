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
import uk.co.real_logic.benchmarks.rtt.MessageRecorder;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.Aeron.connect;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronUtil.launchEmbeddedMediaDriverIfConfigured;

class EchoTest extends AbstractTest<MediaDriver, Aeron, PlainMessageTransceiver, EchoNode>
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

    protected Class<PlainMessageTransceiver> messageTransceiverClass()
    {
        return PlainMessageTransceiver.class;
    }

    protected PlainMessageTransceiver createMessageTransceiver(
        final MediaDriver mediaDriver, final Aeron aeron, final MessageRecorder messageRecorder)
    {
        return new PlainMessageTransceiver(mediaDriver, aeron, true, messageRecorder);
    }
}
