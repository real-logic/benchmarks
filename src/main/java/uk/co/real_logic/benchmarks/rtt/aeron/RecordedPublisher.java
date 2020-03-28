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
import org.agrona.concurrent.status.CountersReader;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.ChannelUri.addSessionId;
import static io.aeron.archive.client.AeronArchive.connect;
import static io.aeron.archive.codecs.SourceLocation.LOCAL;
import static io.aeron.archive.status.RecordingPos.findCounterIdBySession;
import static org.agrona.CloseHelper.closeAll;
import static org.agrona.concurrent.status.CountersReader.NULL_COUNTER_ID;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronUtil.*;
import static uk.co.real_logic.benchmarks.rtt.aeron.BasicPublisher.publishLoop;

public final class RecordedPublisher implements AutoCloseable
{
    private final AtomicBoolean running;
    private final ArchivingMediaDriver archivingMediaDriver;
    private final AeronArchive aeronArchive;
    private final boolean ownsDriver;
    private final ExclusivePublication publication;
    private final Subscription subscription;
    private final long recordingSubscriptionId;

    RecordedPublisher(final AtomicBoolean running)
    {
        this(running, launchArchivingMediaDriver(), connect(), true);
    }

    RecordedPublisher(
        final AtomicBoolean running,
        final ArchivingMediaDriver archivingMediaDriver,
        final AeronArchive aeronArchive,
        final boolean ownsDriver)
    {
        this.running = running;
        this.archivingMediaDriver = archivingMediaDriver;
        this.aeronArchive = aeronArchive;
        this.ownsDriver = ownsDriver;

        final Aeron aeron = aeronArchive.context().aeron();

        final String receiverChannel = receiverChannel();
        final int receiverStreamId = receiverStreamId();
        publication = aeron.addExclusivePublication(receiverChannel, receiverStreamId);

        final int publicationSessionId = publication.sessionId();
        final String recordingChannel = addSessionId(receiverChannel, publicationSessionId);
        recordingSubscriptionId = aeronArchive.startRecording(recordingChannel, receiverStreamId, LOCAL);

        subscription = aeron.addSubscription(senderChannel(), senderStreamId());

        while (!subscription.isConnected() || !publication.isConnected())
        {
            Thread.yield();
        }

        // Wait for recording to have started before publishing.
        final CountersReader counters = aeron.countersReader();
        int counterId;
        do
        {
            counterId = findCounterIdBySession(counters, publicationSessionId);
        }
        while (NULL_COUNTER_ID == counterId);
    }

    public void run()
    {
        publishLoop(publication, subscription, running);
    }


    public void close()
    {
        aeronArchive.stopRecording(recordingSubscriptionId);

        closeAll(publication, subscription);

        if (ownsDriver)
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

        try (RecordedPublisher server = new RecordedPublisher(running))
        {
            server.run();
        }
    }
}
