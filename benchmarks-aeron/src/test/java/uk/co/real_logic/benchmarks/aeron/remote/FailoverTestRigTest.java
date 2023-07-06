/*
 * Copyright 2023 Adaptive Financial Consulting Limited.
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
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.cluster.ConsensusModule;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.service.ClusteredServiceContainer;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import uk.co.real_logic.benchmarks.remote.Configuration;
import uk.co.real_logic.benchmarks.remote.DummyMessageTransceiver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.printingErrorHandler;

class FailoverTestRigTest
{
    private final Deque<AutoCloseable> closeables = new ArrayDeque<>();

    @TempDir(cleanup = CleanupMode.ALWAYS)
    Path tempDir;

    @AfterEach
    void tearDown()
    {
        CloseHelper.closeAll(closeables);
    }

    @Timeout(30)
    @Test
    void test() throws Exception
    {
        launchTestCluster();

        final TestClient testClient = launchTestClient();

        final Configuration configuration = new Configuration.Builder()
            .warmupIterations(5)
            .warmupMessageRate(1000)
            .iterations(15)
            .messageRate(1000)
            .messageLength(20)
            .messageTransceiverClass(DummyMessageTransceiver.class)
            .batchSize(1)
            .outputDirectory(tempDir)
            .outputFileNamePrefix("failover")
            .build();

        final FailoverTransceiver transceiver = new ClusterFailoverTransceiver(testClient.aeronClusterContext);
        final FailoverTestRig rig = new FailoverTestRig(configuration, transceiver);
        rig.run();

        assertEquals(1, findResultFileCount());
    }

    private void launchTestCluster()
    {
        for (int i = 0; i < 3; i++)
        {
            launchTestClusterNode(i);
        }
    }

    private void launchTestClusterNode(final int clusterMemberId)
    {
        final Path driverDir = tempDir.resolve("driver-" + clusterMemberId);
        final Path archiveDir = tempDir.resolve("archive-" + clusterMemberId);
        final Path clusterDir = tempDir.resolve("cluster-" + clusterMemberId);

        final MediaDriver.Context mediaDriverCtx = new MediaDriver.Context()
            .threadingMode(ThreadingMode.SHARED)
            .aeronDirectoryName(driverDir.toString());

        final int archiveControlChannelPort = 8010 + clusterMemberId;
        final Archive.Context archiveCtx = new Archive.Context()
            .threadingMode(ArchiveThreadingMode.SHARED)
            .aeronDirectoryName(mediaDriverCtx.aeronDirectoryName())
            .archiveDirectoryName(archiveDir.toString())
            .controlChannel("aeron:udp?endpoint=localhost:" + archiveControlChannelPort)
            .replicationChannel("aeron:udp?endpoint=localhost:0");

        final ConsensusModule.Context consensusModuleCtx = new ConsensusModule.Context()
            .aeronDirectoryName(mediaDriverCtx.aeronDirectoryName())
            .clusterDirectoryName(clusterDir.toString())
            .clusterMemberId(clusterMemberId)
            .clusterMembers("0,localhost:20000,localhost:20001,localhost:20002,localhost:20003,localhost:8010|" +
            "1,localhost:20004,localhost:20005,localhost:20006,localhost:20007,localhost:8011|" +
            "2,localhost:20008,localhost:20009,localhost:20010,localhost:20011,localhost:8012")
            .ingressChannel("aeron:udp?term-length=64k")
            .replicationChannel("aeron:udp?endpoint=localhost:0")
            .errorHandler(printingErrorHandler("consensus-module"));

        final ClusterFailoverManager failoverManager = new ClusterFailoverManager();

        final ClusteredServiceContainer.Context clusteredServiceContainerCtx = new ClusteredServiceContainer.Context()
            .aeronDirectoryName(mediaDriverCtx.aeronDirectoryName())
            .clusterDirectoryName(consensusModuleCtx.clusterDirectoryName())
            .clusteredService(new FailoverClusteredService(failoverManager))
            .errorHandler(printingErrorHandler("service-container"));

        MediaDriver mediaDriver = null;
        Archive archive = null;
        ConsensusModule consensusModule = null;
        ClusteredServiceContainer clusteredServiceContainer = null;

        try
        {
            mediaDriver = MediaDriver.launch(mediaDriverCtx);
            archive = Archive.launch(archiveCtx);
            consensusModule = ConsensusModule.launch(consensusModuleCtx);
            clusteredServiceContainer = ClusteredServiceContainer.launch(clusteredServiceContainerCtx);

            failoverManager.setConsensusModule(consensusModule);
            failoverManager.setClusteredServiceContainer(clusteredServiceContainer);

            final TestClusterNode testClusterNode = new TestClusterNode(
                mediaDriver,
                archive,
                consensusModule,
                clusteredServiceContainer);

            closeables.addFirst(testClusterNode);
        }
        catch (final Exception e)
        {
            try
            {
                CloseHelper.closeAll(clusteredServiceContainer, consensusModule, archive, mediaDriver);
            }
            catch (final Exception ce)
            {
                e.addSuppressed(ce);
            }

            throw e;
        }
    }

    private TestClient launchTestClient()
    {
        final Path driverDir = tempDir.resolve("client-driver");

        final MediaDriver.Context mediaDriverCtx = new MediaDriver.Context()
            .threadingMode(ThreadingMode.SHARED)
            .aeronDirectoryName(driverDir.toString());

        final AeronCluster.Context aeronClusterContext = new AeronCluster.Context()
            .aeronDirectoryName(mediaDriverCtx.aeronDirectoryName())
            .ingressChannel("aeron:udp?term-length=64k")
            .ingressEndpoints("0=localhost:20000,1=localhost:20004,2=localhost:20008")
            .egressChannel("aeron:udp?endpoint=localhost:0|term-length=64k");

        final MediaDriver mediaDriver = MediaDriver.launch(mediaDriverCtx);

        final TestClient testClient = new TestClient(mediaDriver, aeronClusterContext);

        closeables.addFirst(testClient);

        return testClient;
    }

    private long findResultFileCount() throws IOException
    {
        try (Stream<Path> files = Files.walk(tempDir, 1))
        {
            return files.filter(p -> p.getFileName().toString().endsWith("-raw.csv")).count();
        }
    }

    private static class TestClusterNode implements AutoCloseable
    {
        private final MediaDriver mediaDriver;
        private final Archive archive;
        private final ConsensusModule consensusModule;
        private final ClusteredServiceContainer clusteredServiceContainer;

        TestClusterNode(
            final MediaDriver mediaDriver,
            final Archive archive,
            final ConsensusModule consensusModule,
            final ClusteredServiceContainer clusteredServiceContainer)
        {
            this.mediaDriver = mediaDriver;
            this.archive = archive;
            this.consensusModule = consensusModule;
            this.clusteredServiceContainer = clusteredServiceContainer;
        }

        public void close()
        {
            CloseHelper.closeAll(clusteredServiceContainer, consensusModule, archive, mediaDriver);
        }
    }

    private static class TestClient implements AutoCloseable
    {
        private final MediaDriver mediaDriver;
        private final AeronCluster.Context aeronClusterContext;

        TestClient(
            final MediaDriver mediaDriver,
            final AeronCluster.Context aeronClusterContext)
        {
            this.mediaDriver = mediaDriver;
            this.aeronClusterContext = aeronClusterContext;
        }

        public void close()
        {
            mediaDriver.close();
        }
    }
}
