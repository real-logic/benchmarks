/*
 * Copyright 2015-2023 Real Logic Limited.
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
import io.aeron.cluster.ClusterBackup;
import io.aeron.cluster.service.ClusterMarkFile;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SystemEpochClock;

import java.io.File;
import java.util.Properties;

import static io.aeron.cluster.codecs.mark.ClusterComponentType.BACKUP;
import static io.aeron.cluster.service.ClusteredServiceContainer.Configuration.LIVENESS_TIMEOUT_MS;
import static org.agrona.PropertyAction.PRESERVE;
import static org.agrona.PropertyAction.REPLACE;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.rethrowingErrorHandler;
import static uk.co.real_logic.benchmarks.util.PropertiesUtil.loadPropertiesFiles;
import static uk.co.real_logic.benchmarks.util.PropertiesUtil.mergeWithSystemProperties;

public final class ClusterBackupNode
{
    public static void main(final String[] args)
    {
        mergeWithSystemProperties(PRESERVE, loadPropertiesFiles(new Properties(), REPLACE, args));

        final Archive.Context archiveContext = new Archive.Context()
            .deleteArchiveOnStart(true)
            .recordingEventsEnabled(false);

        final ClusterBackup.Context clusterBackupContext = new ClusterBackup.Context()
            .deleteDirOnStart(true)
            .errorHandler(rethrowingErrorHandler("cluster-backup"))
            .aeronDirectoryName(archiveContext.aeronDirectoryName())
            .epochClock(SystemEpochClock.INSTANCE);

        clusterBackupContext.clusterMarkFile(new ClusterMarkFile(
            new File(archiveContext.aeronDirectoryName(), ClusterMarkFile.FILENAME),
            BACKUP,
            clusterBackupContext.errorBufferLength(),
            clusterBackupContext.epochClock(),
            LIVENESS_TIMEOUT_MS));

        try (Archive archive = Archive.launch(archiveContext);
            ClusterBackup clusterBackup = ClusterBackup.launch(clusterBackupContext))
        {
            new ShutdownSignalBarrier().await();
        }
    }
}
