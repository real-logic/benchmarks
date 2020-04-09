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
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.ArchiveException;
import io.aeron.driver.MediaDriver;
import org.agrona.SystemUtil;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.ChannelUri.addSessionId;
import static io.aeron.archive.client.AeronArchive.connect;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronUtil.*;

/**
 * Remote node which subscribes to the replay channel of the archive and send replayed messages to the sender.
 * Counterpart for the {@link ArchiveMessageTransceiver}.
 */
public final class LiveReplayNode implements AutoCloseable, Runnable
{
    private final ExclusivePublication publication;
    private final Subscription subscription;
    private final AtomicBoolean running;
    private final MediaDriver mediaDriver;
    private final AeronArchive aeronArchive;
    private final boolean ownsArchiveClient;
    private final long replaySessionId;

    LiveReplayNode(final AtomicBoolean running)
    {
        this(running, launchEmbeddedMediaDriverIfConfigured(), connect(), true);
    }

    LiveReplayNode(
        final AtomicBoolean running,
        final MediaDriver mediaDriver,
        final AeronArchive aeronArchive,
        final boolean ownsArchiveClient)
    {
        this.running = running;
        this.mediaDriver = mediaDriver;
        this.aeronArchive = aeronArchive;
        this.ownsArchiveClient = ownsArchiveClient;

        final Aeron aeron = aeronArchive.context().aeron();

        publication = aeron.addExclusivePublication(receiveChannel(), receiveStreamId());

        while (!publication.isConnected())
        {
            yieldUninterruptedly();
        }

        final long recordingId = findLastRecordingId(aeronArchive, archiveChannel(), archiveStreamId());

        final String replayChannel = sendChannel();
        final int replayStreamId = sendStreamId();
        replaySessionId = replayFullRecording(aeronArchive, recordingId, replayChannel, replayStreamId);

        final String channel = addSessionId(replayChannel, (int)replaySessionId);
        subscription = aeron.addSubscription(channel, replayStreamId);

        while (!subscription.isConnected())
        {
            yieldUninterruptedly();
        }
    }

    public void run()
    {
        pipeMessages(subscription, publication, running);
    }

    public void close()
    {
        try
        {
            aeronArchive.stopReplay(replaySessionId);
        }
        catch (final ArchiveException ex)
        {
            System.out.println("WARN: " + ex.getMessage());
        }

        closeAll(subscription, publication);

        if (ownsArchiveClient)
        {
            closeAll(aeronArchive, mediaDriver);
            if (null != mediaDriver)
            {
                mediaDriver.context().deleteAeronDirectory();
            }
        }
    }

    public static void main(final String[] args)
    {
        SystemUtil.loadPropertiesFiles(args);

        final AtomicBoolean running = new AtomicBoolean(true);
        installSignalHandler(running);

        try (LiveReplayNode server = new LiveReplayNode(running))
        {
            server.run();
        }
    }
}
