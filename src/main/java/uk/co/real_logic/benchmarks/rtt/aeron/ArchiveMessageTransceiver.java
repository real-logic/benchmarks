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
import uk.co.real_logic.benchmarks.rtt.MessageRecorder;

import static io.aeron.ChannelUri.addSessionId;
import static io.aeron.archive.client.AeronArchive.connect;
import static io.aeron.archive.codecs.SourceLocation.LOCAL;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronUtil.*;

/**
 * Implementation of the {@link uk.co.real_logic.benchmarks.rtt.MessageTransceiver} interface for benchmarking
 * live replay from local archive to remote node. Used together with the {@link LiveReplayNode}.
 */
public final class ArchiveMessageTransceiver extends AbstractMessageTransceiver
{
    private final ArchivingMediaDriver archivingMediaDriver;
    private final AeronArchive aeronArchive;
    private final boolean ownsArchiveClient;

    public ArchiveMessageTransceiver(final MessageRecorder messageRecorder)
    {
        this(launchArchivingMediaDriver(false), connect(), true, messageRecorder);
    }

    ArchiveMessageTransceiver(
        final ArchivingMediaDriver archivingMediaDriver,
        final AeronArchive aeronArchive,
        final boolean ownsArchiveClient,
        final MessageRecorder messageRecorder)
    {
        super(messageRecorder);
        this.archivingMediaDriver = archivingMediaDriver;
        this.aeronArchive = aeronArchive;
        this.ownsArchiveClient = ownsArchiveClient;
    }

    protected ExclusivePublication createPublication()
    {
        final Aeron aeron = aeronArchive.context().aeron();

        final String archiveChannel = archiveChannel();
        final int archiveStreamId = archiveStreamId();
        final ExclusivePublication publication = aeron.addExclusivePublication(archiveChannel, archiveStreamId);

        final int publicationSessionId = publication.sessionId();
        final String channel = addSessionId(archiveChannel, publicationSessionId);
        aeronArchive.startRecording(channel, archiveStreamId, LOCAL, true);

        awaitRecordingStart(aeron, publicationSessionId);
        return publication;
    }

    protected Subscription createSubscription()
    {
        return aeronArchive.context().aeron().addSubscription(receiveChannel(), receiveStreamId());
    }

    public void destroy() throws Exception
    {
        super.destroy();

        if (ownsArchiveClient)
        {
            closeAll(aeronArchive, archivingMediaDriver);
            archivingMediaDriver.mediaDriver().context().deleteAeronDirectory();
            archivingMediaDriver.archive().context().deleteDirectory();
        }
    }
}
