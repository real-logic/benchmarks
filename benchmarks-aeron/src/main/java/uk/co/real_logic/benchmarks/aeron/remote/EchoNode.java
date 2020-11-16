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
package uk.co.real_logic.benchmarks.aeron.remote;

import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.SystemUtil;
import org.agrona.concurrent.IdleStrategy;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.Aeron.connect;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

/**
 * Remote node which echoes original messages back to the sender.
 */
public final class EchoNode implements AutoCloseable, Runnable
{
    private final ExclusivePublication publication;
    private final Subscription subscription;
    private final Subscription[] passiveSubscriptions;
    private final Image[] passiveImages;
    private final AtomicBoolean running;
    private final MediaDriver mediaDriver;
    private final Aeron aeron;
    private final boolean ownsAeronClient;

    EchoNode(final AtomicBoolean running)
    {
        this(running, launchEmbeddedMediaDriverIfConfigured(), connect(), true);
    }

    EchoNode(
        final AtomicBoolean running, final MediaDriver mediaDriver, final Aeron aeron, final boolean ownsAeronClient)
    {
        this.running = running;
        this.mediaDriver = mediaDriver;
        this.aeron = aeron;
        this.ownsAeronClient = ownsAeronClient;

        publication = aeron.addExclusivePublication(sourceChannels()[0], sourceStreams()[0]);
        subscription = aeron.addSubscription(destinationChannels()[0], destinationStreams()[0]);

        final String[] passiveChannels = passiveChannels();
        final int[] passiveStreams = passiveStreams();
        if (passiveChannels.length != passiveStreams.length)
        {
            throw new IllegalStateException("Number of passive channels does not match with passive streams: " +
                Arrays.toString(passiveChannels) + ", " + Arrays.toString(passiveStreams));
        }

        if (passiveChannels.length > 0)
        {
            passiveSubscriptions = new Subscription[passiveChannels.length];
            passiveImages = new Image[passiveChannels.length];
            for (int i = 0; i < passiveChannels.length; i++)
            {
                passiveSubscriptions[i] = aeron.addSubscription(passiveChannels[i], passiveStreams[i]);
            }
        }
        else
        {
            passiveSubscriptions = EMPTY_SUBSCRIPTIONS;
            passiveImages = EMPTY_IMAGES;
        }

        while (!subscription.isConnected() || !publication.isConnected() || !allConnected(passiveSubscriptions))
        {
            yieldUninterruptedly();
        }

        reloadImages(passiveSubscriptions, passiveImages);
    }

    public void run()
    {
        final ExclusivePublication publication = this.publication;
        final Subscription subscription = this.subscription;
        final Subscription[] passiveSubscriptions = this.passiveSubscriptions;
        final Image[] passiveImages = this.passiveImages;

        final IdleStrategy idleStrategy = idleStrategy();
        final BufferClaim bufferClaim = new BufferClaim();
        final FragmentHandler dataHandler =
            (buffer, offset, length, header) ->
            {
                long result;
                while ((result = publication.tryClaim(length, bufferClaim)) <= 0)
                {
                    checkPublicationResult(result);
                }

                bufferClaim
                    .flags(header.flags())
                    .putBytes(buffer, offset, length)
                    .commit();
            };

        final boolean reconnectIfImageClosed = reconnectIfImageClosed();
        final int passiveChannelsPollFrequency = passiveChannelsPollFrequency();
        final AtomicBoolean running = this.running;

        Image image = subscription.imageAtIndex(0);
        int iterationsSinceLastPoll = 0;
        while (true)
        {
            final int fragmentsRead = image.poll(dataHandler, FRAGMENT_LIMIT);
            if (0 == fragmentsRead)
            {
                if (!running.get())
                {
                    break;
                }
                else if (image.isClosed())
                {
                    if (!reconnectIfImageClosed)
                    {
                        break;
                    }
                    while (!subscription.isConnected() ||
                        !publication.isConnected() ||
                        !allConnected(passiveSubscriptions))
                    {
                        yieldUninterruptedly();
                    }
                    image = subscription.imageAtIndex(0);
                    reloadImages(passiveSubscriptions, passiveImages);
                }
            }

            iterationsSinceLastPoll++;
            if (EMPTY_IMAGES != passiveImages && iterationsSinceLastPoll >= passiveChannelsPollFrequency)
            {
                iterationsSinceLastPoll = 0;
                for (int i = 0; i < passiveImages.length; i++)
                {
                    passiveImages[i].poll(NULL_FRAGMENT_HANDLER, FRAGMENT_LIMIT);
                }
            }

            idleStrategy.idle(fragmentsRead);
        }
    }

    public void close()
    {
        closeAll(passiveSubscriptions);
        closeAll(subscription, publication);

        if (ownsAeronClient)
        {
            closeAll(aeron, mediaDriver);
        }
    }

    private static void reloadImages(final Subscription[] subscriptions, final Image[] images)
    {
        for (int i = 0; i < subscriptions.length; i++)
        {
            images[i] = subscriptions[i].imageAtIndex(0);
        }
    }

    public static void main(final String[] args)
    {
        SystemUtil.loadPropertiesFiles(args);

        final AtomicBoolean running = new AtomicBoolean(true);
        installSignalHandler(running);

        try (EchoNode server = new EchoNode(running))
        {
            server.run();
        }
    }
}
