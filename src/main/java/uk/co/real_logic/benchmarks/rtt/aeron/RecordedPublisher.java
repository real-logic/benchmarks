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
import org.agrona.SystemUtil;
import org.agrona.concurrent.SigInt;
import org.agrona.concurrent.status.CountersReader;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.ChannelUri.addSessionId;
import static io.aeron.archive.codecs.SourceLocation.LOCAL;
import static io.aeron.archive.status.RecordingPos.findCounterIdBySession;
import static org.agrona.concurrent.status.CountersReader.NULL_COUNTER_ID;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronLauncher.receiverChannel;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronLauncher.receiverStreamId;

public final class RecordedPublisher extends BasicPublisher
{
    private long recordingSubscriptionId;

    RecordedPublisher(final AtomicBoolean running)
    {
        this(running, new AeronLauncher(), true);
    }

    RecordedPublisher(final AtomicBoolean running, final AeronLauncher launcher, final boolean ownsLauncher)
    {
        super(running, launcher, ownsLauncher);
    }

    ExclusivePublication createPublication()
    {
        final ExclusivePublication publication = launcher.aeron()
            .addExclusivePublication(receiverChannel(), receiverStreamId());

        final int publicationSessionId = publication.sessionId();
        final String recordingChannel = addSessionId(receiverChannel(), publicationSessionId);
        recordingSubscriptionId = launcher.aeronArchive().startRecording(recordingChannel, receiverStreamId(), LOCAL);

        // Wait for recording to have started before publishing.
        final CountersReader counters = launcher.aeron().countersReader();
        int counterId;
        do
        {
            counterId = findCounterIdBySession(counters, publicationSessionId);
        }
        while (NULL_COUNTER_ID == counterId);

        return publication;
    }

    public void close()
    {
        launcher.aeronArchive().stopRecording(recordingSubscriptionId);

        super.close();
    }

    public static void main(final String[] args)
    {
        SystemUtil.loadPropertiesFiles(args);

        final AtomicBoolean running = new AtomicBoolean(true);

        // Register a SIGINT handler for graceful shutdown.
        SigInt.register(() -> running.set(false));

        try (RecordedPublisher server = new RecordedPublisher(running))
        {
            server.run();
        }
    }
}
