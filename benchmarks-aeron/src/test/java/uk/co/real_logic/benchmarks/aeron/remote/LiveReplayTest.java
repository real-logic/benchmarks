/*
 * Copyright 2015-2021 Real Logic Limited.
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

import io.aeron.archive.client.AeronArchive;
import org.agrona.IoUtil;
import org.junit.jupiter.api.AfterEach;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.archive.client.AeronArchive.connect;
import static uk.co.real_logic.benchmarks.aeron.remote.ArchivingMediaDriver.launchArchiveWithEmbeddedDriver;

class LiveReplayTest extends
    AbstractTest<ArchivingMediaDriver, AeronArchive, LiveReplayMessageTransceiver, ArchiveNode>
{

    private File archiveDir;

    @AfterEach
    void afterEach()
    {
        IoUtil.delete(archiveDir, true);
    }

    protected ArchiveNode createNode(
        final AtomicBoolean running, final ArchivingMediaDriver archivingMediaDriver, final AeronArchive aeronArchive)
    {
        return new ArchiveNode(running, archivingMediaDriver, aeronArchive, false);
    }

    protected ArchivingMediaDriver createDriver()
    {
        final ArchivingMediaDriver driver = launchArchiveWithEmbeddedDriver();
        archiveDir = driver.archive.context().archiveDir();
        return driver;
    }

    protected AeronArchive connectToDriver()
    {
        return connect();
    }

    protected Class<LiveReplayMessageTransceiver> messageTransceiverClass()
    {
        return LiveReplayMessageTransceiver.class;
    }

    protected LiveReplayMessageTransceiver createMessageTransceiver(
        final ArchivingMediaDriver archivingMediaDriver,
        final AeronArchive aeronArchive)
    {
        return new LiveReplayMessageTransceiver(null, aeronArchive, false);
    }
}
