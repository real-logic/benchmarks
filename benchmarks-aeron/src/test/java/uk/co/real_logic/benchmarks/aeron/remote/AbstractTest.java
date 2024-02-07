/*
 * Copyright 2015-2024 Real Logic Limited.
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

import org.HdrHistogram.Histogram;
import org.HdrHistogram.ValueRecorder;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.SystemNanoClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import uk.co.real_logic.benchmarks.remote.*;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.aeron.archive.Archive.Configuration.ARCHIVE_DIR_DELETE_ON_START_PROP_NAME;
import static io.aeron.archive.client.AeronArchive.Configuration.RECORDING_EVENTS_ENABLED_PROP_NAME;
import static io.aeron.driver.Configuration.DIR_DELETE_ON_SHUTDOWN_PROP_NAME;
import static io.aeron.driver.Configuration.DIR_DELETE_ON_START_PROP_NAME;
import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static org.agrona.LangUtil.rethrowUnchecked;
import static org.mockito.Mockito.mock;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

abstract class AbstractTest<
    DRIVER extends AutoCloseable,
    CLIENT extends AutoCloseable,
    MESSAGE_TRANSCEIVER extends MessageTransceiver,
    NODE extends AutoCloseable & Runnable>
{
    @BeforeEach
    void before()
    {
        setProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME, "true");
        setProperty(RECORDING_EVENTS_ENABLED_PROP_NAME, "false");
        setProperty(DIR_DELETE_ON_START_PROP_NAME, "true");
        setProperty(DIR_DELETE_ON_SHUTDOWN_PROP_NAME, "true");
        setProperty(ARCHIVE_DIR_DELETE_ON_START_PROP_NAME, "true");
    }

    @AfterEach
    void after()
    {
        clearProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME);
        clearProperty(RECORDING_EVENTS_ENABLED_PROP_NAME);
        clearProperty(DIR_DELETE_ON_START_PROP_NAME);
        clearProperty(DIR_DELETE_ON_SHUTDOWN_PROP_NAME);
        clearProperty(ARCHIVE_DIR_DELETE_ON_START_PROP_NAME);
        clearProperty(DESTINATION_CHANNELS_PROP_NAME);
        clearProperty(DESTINATION_STREAMS_PROP_NAME);
        clearProperty(SOURCE_CHANNELS_PROP_NAME);
    }

    @Timeout(30)
    @Test
    void smallMessage(final @TempDir Path tempDir) throws Exception
    {
        setProperty(SOURCE_CHANNELS_PROP_NAME, "aeron:udp?endpoint=localhost:13334|mtu=2k|term-length=64k");
        setProperty(DESTINATION_CHANNELS_PROP_NAME, "aeron:udp?endpoint=localhost:13333|mtu=2k|term-length=64k");
        test(10_000, 111, 10, tempDir);
    }

    @Timeout(30)
    @Test
    void mediumMessage(final @TempDir Path tempDir) throws Exception
    {
        test(1000, 288, 5, tempDir);
    }

    @Timeout(30)
    @Test
    void largeMessage(final @TempDir Path tempDir) throws Exception
    {
        test(100, 1344, 1, tempDir);
    }

    @SuppressWarnings("MethodLength")
    protected final void test(
        final int messageRate,
        final int messageLength,
        final int burstSize,
        final Path tempDir) throws Exception
    {
        final Configuration configuration = new Configuration.Builder()
            .warmupIterations(0)
            .iterations(1)
            .messageRate(messageRate)
            .messageLength(messageLength)
            .messageTransceiverClass(messageTransceiverClass())
            .batchSize(burstSize)
            .outputDirectory(tempDir)
            .outputFileNamePrefix("aeron")
            .build();

        final AtomicReference<Throwable> error = new AtomicReference<>();

        try (DRIVER driver = createDriver(); CLIENT client = connectToDriver())
        {
            final AtomicBoolean running = new AtomicBoolean(true);
            final CountDownLatch remoteNodeStarted = new CountDownLatch(1);
            final Thread remoteNode = new Thread(
                () ->
                {
                    remoteNodeStarted.countDown();

                    try (NODE node = createNode(running, driver, client))
                    {
                        node.run();
                    }
                    catch (final Throwable t)
                    {
                        error.set(t);
                    }
                });
            try
            {
                remoteNode.setName("remote-node");
                remoteNode.setDaemon(true);
                remoteNode.start();

                final NanoClock nanoClock = SystemNanoClock.INSTANCE;
                final PersistedHistogram persistedHistogram = new SinglePersistedHistogram(new Histogram(3));

                final LoadTestRig loadTestRig = new LoadTestRig(
                    configuration,
                    nanoClock,
                    persistedHistogram,
                    (nc, ph) -> createMessageTransceiver(nc, ph, driver, client),
                    mock(PrintStream.class));

                remoteNodeStarted.await();
                loadTestRig.run();
            }
            finally
            {
                running.set(false);
                final boolean wasInterrupted = Thread.interrupted();
                remoteNode.join();
                if (wasInterrupted)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (null != error.get())
        {
            rethrowUnchecked(error.get());
        }
    }

    abstract NODE createNode(AtomicBoolean running, DRIVER driver, CLIENT client);

    abstract DRIVER createDriver();

    abstract CLIENT connectToDriver();

    abstract Class<MESSAGE_TRANSCEIVER> messageTransceiverClass();

    abstract MESSAGE_TRANSCEIVER createMessageTransceiver(
        NanoClock nanoClock,
        ValueRecorder valueRecorder,
        DRIVER driver,
        CLIENT client);
}
