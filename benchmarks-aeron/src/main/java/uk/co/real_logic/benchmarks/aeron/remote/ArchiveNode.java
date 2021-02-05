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

import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;
import org.agrona.PropertyAction;
import org.agrona.SystemUtil;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.ChannelUri.addSessionId;
import static io.aeron.archive.client.AeronArchive.connect;
import static io.aeron.archive.codecs.SourceLocation.LOCAL;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

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

    ArchiveNode(final AtomicBoolean running)
    {
        this(running, launchArchivingMediaDriver(), connect(), true);
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

        subscription = aeron.addSubscription(destinationChannels()[0], destinationStreams()[0]);

        final String archiveChannel = archiveChannel();
        final int archiveStreamId = archiveStream();
        publication = aeron.addExclusivePublication(archiveChannel, archiveStreamId);

        final int publicationSessionId = publication.sessionId();
        final String channel = addSessionId(archiveChannel, publicationSessionId);
        aeronArchive.startRecording(channel, archiveStreamId, LOCAL, true);

        while (!subscription.isConnected() || !publication.isConnected())
        {
            yieldUninterruptedly();
        }

        awaitRecordingStart(aeron, publicationSessionId);
    }

    public void run()
    {
        pipeMessages(subscription, publication, running);
    }

    public void close()
    {
        closeAll(publication, subscription);

        if (ownsArchiveClient)
        {
            closeAll(aeronArchive, archivingMediaDriver);
        }
    }

    public static void main(final String[] args)
    {
        SystemUtil.loadPropertiesFiles(PropertyAction.PRESERVE, args);

        final AtomicBoolean running = new AtomicBoolean(true);
        installSignalHandler(running);

        try (ArchiveNode server = new ArchiveNode(running))
        {
            server.run();
        }
    }
}
