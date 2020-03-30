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
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import io.aeron.archive.ArchivingMediaDriver;
import io.aeron.archive.client.AeronArchive;
import org.agrona.SystemUtil;
import org.agrona.concurrent.SigInt;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.ChannelUri.addSessionId;
import static io.aeron.archive.client.AeronArchive.connect;
import static io.aeron.archive.codecs.SourceLocation.LOCAL;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronUtil.*;

/**
 * Remote node which archives received messages and replays persisted messages back to the sender.
 * Counterpart for the {@link LiveReplayMessageTransceiver}.
 */
public final class ArchiveNode implements AutoCloseable, Runnable
{
    private final AtomicBoolean running;
    private final ArchivingMediaDriver archivingMediaDriver;
    private final AeronArchive aeronArchive;
    private final boolean ownsArchiveClient;
    private final ExclusivePublication publication;
    private final Subscription subscription;
    private final long recordingSubscriptionId;

    ArchiveNode(final AtomicBoolean running)
    {
        this(running, launchArchivingMediaDriver(false), connect(), true);
    }

    ArchiveNode(
        final AtomicBoolean running,
        final ArchivingMediaDriver archivingMediaDriver,
        final AeronArchive aeronArchive,
        final boolean ownsArchiveClient)
    {
        this.running = running;
        this.archivingMediaDriver = archivingMediaDriver;
        this.aeronArchive = aeronArchive;
        this.ownsArchiveClient = ownsArchiveClient;

        final Aeron aeron = aeronArchive.context().aeron();

        subscription = aeron.addSubscription(sendChannel(), sendStreamId());

        final String archiveChannel = archiveChannel();
        final int archiveStreamId = archiveStreamId();
        publication = aeron.addExclusivePublication(archiveChannel, archiveStreamId);

        final int publicationSessionId = publication.sessionId();
        final String channel = addSessionId(archiveChannel, publicationSessionId);
        recordingSubscriptionId = aeronArchive.startRecording(channel, archiveStreamId, LOCAL);

        while (!subscription.isConnected() || !publication.isConnected())
        {
            Thread.yield();
        }

        awaitRecordingStart(aeron, publicationSessionId);
    }

    public void run()
    {
        publishLoop(publication, subscription, running);
    }


    public void close()
    {
        aeronArchive.stopRecording(recordingSubscriptionId);

        closeAll(publication, subscription);

        if (ownsArchiveClient)
        {
            closeAll(aeronArchive, archivingMediaDriver);
            archivingMediaDriver.mediaDriver().context().deleteAeronDirectory();
            archivingMediaDriver.archive().context().deleteDirectory();
        }
    }

    public static void main(final String[] args)
    {
        SystemUtil.loadPropertiesFiles(args);

        final AtomicBoolean running = new AtomicBoolean(true);

        // Register a SIGINT handler for graceful shutdown.
        SigInt.register(() -> running.set(false));

        try (ArchiveNode server = new ArchiveNode(running))
        {
            server.run();
        }
    }
}
