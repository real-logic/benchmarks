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
import io.aeron.Image;
import io.aeron.Subscription;
import io.aeron.archive.client.AeronArchive;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.SystemUtil;
import org.agrona.concurrent.SigInt;
import org.agrona.concurrent.status.CountersReader;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.ChannelUri.addSessionId;
import static io.aeron.archive.codecs.SourceLocation.LOCAL;
import static io.aeron.archive.status.RecordingPos.findCounterIdBySession;
import static org.agrona.CloseHelper.closeAll;
import static org.agrona.concurrent.status.CountersReader.NULL_COUNTER_ID;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronLauncher.*;
import static uk.co.real_logic.benchmarks.rtt.aeron.BasicMessagePump.checkResult;

public final class RecordedPublisher implements AutoCloseable
{
    private final ExclusivePublication publication;
    private final Subscription subscription;
    private final AtomicBoolean running;
    private final AeronLauncher launcher;
    private final boolean ownsLauncher;
    private final long recordingSubscriptionId;

    RecordedPublisher(final AtomicBoolean running)
    {
        this(running, new AeronLauncher(), true);
    }

    RecordedPublisher(final AtomicBoolean running, final AeronLauncher launcher, final boolean ownsLauncher)
    {
        this.running = running;
        this.launcher = launcher;
        this.ownsLauncher = ownsLauncher;

        final Aeron aeron = launcher.aeron();
        final AeronArchive aeronArchive = launcher.aeronArchive();

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
        final ExclusivePublication publication = this.publication;
        final FragmentHandler dataHandler =
            (buffer, offset, length, header) ->
            {
                long result;
                while ((result = publication.offer(buffer, offset, length)) < 0L)
                {
                    checkResult(result);
                }
            };

        final AtomicBoolean running = this.running;
        final Image image = subscription.imageAtIndex(0);
        final int frameCountLimit = frameCountLimit();
        while (running.get())
        {
            final int fragments = image.poll(dataHandler, frameCountLimit);
            if (0 == fragments && image.isClosed())
            {
                throw new IllegalStateException("sender image closed");
            }
        }
    }

    public void close()
    {
        launcher.aeronArchive().stopRecording(recordingSubscriptionId);

        closeAll(publication, subscription);

        if (ownsLauncher)
        {
            launcher.close();
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
