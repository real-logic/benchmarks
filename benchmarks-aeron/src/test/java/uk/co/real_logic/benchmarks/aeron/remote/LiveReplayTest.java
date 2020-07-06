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

import io.aeron.archive.client.AeronArchive;
import org.agrona.LangUtil;
import org.agrona.collections.MutableInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import uk.co.real_logic.benchmarks.remote.Configuration;
import uk.co.real_logic.benchmarks.remote.MessageRecorder;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.aeron.archive.client.AeronArchive.connect;
import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.RECONNECT_IF_IMAGE_CLOSED;
import static uk.co.real_logic.benchmarks.aeron.remote.ArchivingMediaDriver.launchArchiveWithEmbeddedDriver;

class LiveReplayTest extends
    AbstractTest<ArchivingMediaDriver, AeronArchive, LiveReplayMessageTransceiver, ArchiveNode>
{
    protected ArchiveNode createNode(
        final AtomicBoolean running, final ArchivingMediaDriver archivingMediaDriver, final AeronArchive aeronArchive)
    {
        return new ArchiveNode(running, archivingMediaDriver, aeronArchive, false);
    }

    protected ArchivingMediaDriver createDriver()
    {
        return launchArchiveWithEmbeddedDriver();
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
        final AeronArchive aeronArchive,
        final MessageRecorder messageRecorder)
    {
        return new LiveReplayMessageTransceiver(null, aeronArchive, false, messageRecorder);
    }

    @Timeout(30)
    @Test
    void replayShouldContinueFromWhereLastRecordingStopped(final @TempDir Path tempDir) throws Exception
    {
        final Configuration configuration = new Configuration.Builder()
            .numberOfMessages(10)
            .messageLength(24)
            .messageTransceiverClass(messageTransceiverClass())
            .outputDirectory(tempDir)
            .outputFileNamePrefix("replay")
            .build();

        final AtomicReference<Throwable> error = new AtomicReference<>();
        final MutableInteger receiveCount = new MutableInteger();
        final MessageRecorder messageRecorder = (timestamp, checksum) -> receiveCount.increment();

        setProperty(RECONNECT_IF_IMAGE_CLOSED, "true");
        try
        {
            final ArchivingMediaDriver driver = createDriver();
            try (ArchivingMediaDriver ignore = driver;
                AeronArchive aeronArchive = connectToDriver())
            {
                final AtomicBoolean running = new AtomicBoolean(true);
                final Thread archiveThread = new Thread(() ->
                {
                    try (ArchiveNode archiveNode = createNode(running, driver, aeronArchive))
                    {
                        archiveNode.run();
                    }
                    catch (final Throwable t)
                    {
                        t.printStackTrace();
                        error.set(t);
                    }
                });
                archiveThread.setDaemon(true);
                archiveThread.start();

                try
                {
                    for (int i = 1; i <= 5; i++)
                    {
                        final LiveReplayMessageTransceiver messageTransceiver =
                            createMessageTransceiver(driver, aeronArchive, messageRecorder);

                        messageTransceiver.init(configuration);

                        final int received = receiveCount.get();

                        int sent = 0;
                        while (sent < i)
                        {
                            sent += messageTransceiver.send(i - sent, 24, 100 * i, -888 * i);
                            messageTransceiver.receive();
                        }

                        while (receiveCount.get() < received + i)
                        {
                            messageTransceiver.receive();
                        }

                        messageTransceiver.destroy();
                    }

                    assertEquals(15, receiveCount.get());
                }
                finally
                {
                    running.set(false);
                    archiveThread.join();
                }
            }
            finally
            {
                driver.archive.context().deleteDirectory();
            }

            if (error.get() != null)
            {
                LangUtil.rethrowUnchecked(error.get());
            }
        }
        finally
        {
            clearProperty(RECONNECT_IF_IMAGE_CLOSED);
        }
    }
}
