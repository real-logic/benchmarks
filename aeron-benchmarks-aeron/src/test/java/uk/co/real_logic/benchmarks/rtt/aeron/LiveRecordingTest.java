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

import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.archive.client.AeronArchive;
import uk.co.real_logic.benchmarks.rtt.MessageRecorder;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.archive.client.AeronArchive.connect;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronUtil.launchArchivingMediaDriver;

class LiveRecordingTest extends
    AbstractTest<ArchivingMediaDriver, AeronArchive, LiveRecordingMessageTransceiver, EchoNode>
{
    protected EchoNode createNode(
        final AtomicBoolean running, final ArchivingMediaDriver archivingMediaDriver, final AeronArchive aeronArchive)
    {
        return new EchoNode(running, archivingMediaDriver.mediaDriver(), aeronArchive.context().aeron(), false);
    }

    protected ArchivingMediaDriver createDriver()
    {
        return launchArchivingMediaDriver(true);
    }

    protected AeronArchive connectToDriver()
    {
        return connect();
    }

    protected Class<LiveRecordingMessageTransceiver> messageTransceiverClass()
    {
        return LiveRecordingMessageTransceiver.class;
    }

    protected LiveRecordingMessageTransceiver createMessageTransceiver(
        final ArchivingMediaDriver archivingMediaDriver,
        final AeronArchive aeronArchive,
        final MessageRecorder messageRecorder)
    {
        return new LiveRecordingMessageTransceiver(archivingMediaDriver, aeronArchive, false, messageRecorder);
    }
}
