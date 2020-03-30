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

import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.client.ArchiveException;
import io.aeron.driver.MediaDriver;
import uk.co.real_logic.benchmarks.rtt.MessageRecorder;

import static io.aeron.ChannelUri.addSessionId;
import static io.aeron.archive.client.AeronArchive.connect;
import static java.lang.Long.MAX_VALUE;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronUtil.*;

/**
 * Implementation of the {@link uk.co.real_logic.benchmarks.rtt.MessageTransceiver} interface for benchmarking
 * live replay from remote archive. Used together with the {@link ArchiveNode}.
 */
public final class LiveReplayMessageTransceiver extends AbstractMessageTransceiver
{
    private final MediaDriver mediaDriver;
    private final AeronArchive aeronArchive;
    private final boolean ownsArchiveClient;
    private long replaySessionId;

    public LiveReplayMessageTransceiver(final MessageRecorder messageRecorder)
    {
        this(launchEmbeddedMediaDriverIfConfigured(), connect(), true, messageRecorder);
    }

    LiveReplayMessageTransceiver(
        final MediaDriver mediaDriver,
        final AeronArchive aeronArchive,
        final boolean ownsArchiveClient,
        final MessageRecorder messageRecorder)
    {
        super(messageRecorder);
        this.mediaDriver = mediaDriver;
        this.aeronArchive = aeronArchive;
        this.ownsArchiveClient = ownsArchiveClient;
    }

    protected ExclusivePublication createPublication()
    {
        return aeronArchive.context().aeron().addExclusivePublication(sendChannel(), sendStreamId());
    }

    protected Subscription createSubscription()
    {
        final long recordingId = findLastRecordingId(aeronArchive, archiveChannel(), archiveStreamId());

        final String replayChannel = receiveChannel();
        final int replayStreamId = receiveStreamId();
        replaySessionId = aeronArchive.startReplay(recordingId, 0, MAX_VALUE, replayChannel, replayStreamId);

        final String channel = addSessionId(replayChannel, (int)replaySessionId);
        return aeronArchive.context().aeron().addSubscription(channel, replayStreamId);
    }

    public void destroy() throws Exception
    {
        try
        {
            aeronArchive.stopReplay(replaySessionId);
        }
        catch (final ArchiveException ex)
        {
            System.out.println("WARN: " + ex.toString());
        }

        super.destroy();

        if (ownsArchiveClient)
        {
            closeAll(aeronArchive, mediaDriver);
            mediaDriver.context().deleteAeronDirectory();
        }
    }

}
