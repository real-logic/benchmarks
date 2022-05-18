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

import io.aeron.archive.Archive;
import io.aeron.archive.client.AeronArchive;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.service.ClusteredServiceContainer;
import org.HdrHistogram.Histogram;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.NoOpLock;
import org.agrona.concurrent.SystemNanoClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import uk.co.real_logic.benchmarks.remote.Configuration;
import uk.co.real_logic.benchmarks.remote.LoadTestRig;
import uk.co.real_logic.benchmarks.remote.PersistedHistogram;
import uk.co.real_logic.benchmarks.remote.SinglePersistedHistogram;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static io.aeron.CommonContext.AERON_DIR_PROP_NAME;
import static io.aeron.driver.Configuration.DIR_DELETE_ON_SHUTDOWN_PROP_NAME;
import static io.aeron.driver.Configuration.DIR_DELETE_ON_START_PROP_NAME;
import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static org.agrona.LangUtil.rethrowUnchecked;
import static org.mockito.Mockito.mock;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.EMBEDDED_MEDIA_DRIVER_PROP_NAME;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.launchArchivingMediaDriver;

class ClusterTest
{
    @BeforeEach
    void before()
    {
        setProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME, "true");
        setProperty(AeronArchive.Configuration.RECORDING_EVENTS_ENABLED_PROP_NAME, "false");
        setProperty(DIR_DELETE_ON_START_PROP_NAME, "true");
        setProperty(DIR_DELETE_ON_SHUTDOWN_PROP_NAME, "true");
        setProperty(Archive.Configuration.ARCHIVE_DIR_DELETE_ON_START_PROP_NAME, "true");
    }

    @AfterEach
    void after()
    {
        clearProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME);
        clearProperty(DIR_DELETE_ON_START_PROP_NAME);
        clearProperty(DIR_DELETE_ON_SHUTDOWN_PROP_NAME);
        clearProperty(Archive.Configuration.ARCHIVE_DIR_DELETE_ON_START_PROP_NAME);
        clearProperty(AERON_DIR_PROP_NAME);
        clearProperty(Archive.Configuration.ARCHIVE_DIR_PROP_NAME);
        clearProperty(AeronArchive.Configuration.RECORDING_EVENTS_ENABLED_PROP_NAME);
        clearProperty(AeronArchive.Configuration.CONTROL_CHANNEL_PROP_NAME);
        clearProperty(AeronArchive.Configuration.LOCAL_CONTROL_CHANNEL_PROP_NAME);
    }

    @Timeout(30)
    @Test
    void messageLength32bytes(final @TempDir Path tempDir) throws Exception
    {
        test(10_000, 32, 10, tempDir);
    }

    @Timeout(30)
    @Test
    void messageLength192bytes(final @TempDir Path tempDir) throws Exception
    {
        test(1000, 192, 5, tempDir);
    }

    @Timeout(30)
    @Test
    void messageLength1344bytes(final @TempDir Path tempDir) throws Exception
    {
        test(100, 1344, 1, tempDir);
    }

    @SuppressWarnings("MethodLength")
    protected final void test(
        final int messages,
        final int messageLength,
        final int burstSize,
        final Path tempDir) throws Exception
    {
        final String aeronDirectoryName = tempDir.resolve("driver").toString();
        setProperty(AERON_DIR_PROP_NAME, aeronDirectoryName);
        setProperty(Archive.Configuration.ARCHIVE_DIR_PROP_NAME, tempDir.resolve("archive").toString());
        setProperty(AeronArchive.Configuration.CONTROL_CHANNEL_PROP_NAME,
            "aeron:udp?endpoint=localhost:8010|term-length=64k");
        setProperty(AeronArchive.Configuration.LOCAL_CONTROL_CHANNEL_PROP_NAME, "aeron:ipc?term-length=64k");

        final Configuration configuration = new Configuration.Builder()
            .warmupIterations(0)
            .iterations(2)
            .messageRate(messages)
            .messageLength(messageLength)
            .messageTransceiverClass(ClusterMessageTransceiver.class)
            .batchSize(burstSize)
            .outputDirectory(tempDir)
            .outputFileNamePrefix("aeron")
            .build();

        final AtomicReference<Throwable> error = new AtomicReference<>();

        final AeronArchive.Context aeronArchiveContext = new AeronArchive.Context()
            .lock(NoOpLock.INSTANCE)
            .controlRequestChannel(AeronArchive.Configuration.localControlChannel())
            .controlRequestStreamId(AeronArchive.Configuration.localControlStreamId())
            .controlResponseChannel(AeronArchive.Configuration.localControlChannel())
            .aeronDirectoryName(aeronDirectoryName);

        final String clusterDirectoryName = tempDir.resolve("consensus-module").toString();

        final ConsensusModule.Context consensusModuleContext = new ConsensusModule.Context()
            .clusterMemberId(0)
            .clusterMembers("0,localhost:20000,localhost:20001,localhost:20002,localhost:20003,localhost:8010")
            .ingressChannel("aeron:udp?term-length=64k")
            .logChannel("aeron:udp?term-length=64k|control-mode=manual|control=localhost:20002")
            .replicationChannel("aeron:udp?endpoint=localhost:0")
            .errorHandler(AeronUtil.rethrowingErrorHandler("consensus-module"))
            .archiveContext(aeronArchiveContext.clone())
            .aeronDirectoryName(aeronDirectoryName)
            .clusterDirectoryName(clusterDirectoryName);

        final ClusteredServiceContainer.Context serviceContainerContext = new ClusteredServiceContainer.Context()
            .clusteredService(new EchoClusteredService(configuration.snapshotSize()))
            .errorHandler(AeronUtil.rethrowingErrorHandler("service-container"))
            .archiveContext(aeronArchiveContext.clone())
            .aeronDirectoryName(aeronDirectoryName)
            .clusterDirectoryName(clusterDirectoryName);

        final AeronCluster.Context aeronClusterContext = new AeronCluster.Context()
            .ingressChannel("aeron:udp?term-length=64k")
            .ingressEndpoints("0=localhost:20000")
            .egressChannel("aeron:udp?endpoint=localhost:0|term-length=64k");

        try (ArchivingMediaDriver driver = launchArchivingMediaDriver();
            ConsensusModule consensusModule = ConsensusModule.launch(consensusModuleContext);
            ClusteredServiceContainer clusteredServiceContainer = ClusteredServiceContainer.launch(
                serviceContainerContext))
        {
            final NanoClock nanoClock = SystemNanoClock.INSTANCE;
            final PersistedHistogram persistedHistogram = new SinglePersistedHistogram(new Histogram(3));
            final LoadTestRig loadTestRig = new LoadTestRig(
                configuration,
                nanoClock,
                persistedHistogram,
                (nc, vr) -> new ClusterMessageTransceiver(nc, vr, null, aeronClusterContext),
                mock(PrintStream.class));
            loadTestRig.run();
        }

        if (null != error.get())
        {
            rethrowUnchecked(error.get());
        }
    }
}
