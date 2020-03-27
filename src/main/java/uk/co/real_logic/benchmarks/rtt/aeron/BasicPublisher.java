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
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.SystemUtil;
import org.agrona.concurrent.SigInt;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronUtil.*;

public final class BasicPublisher implements AutoCloseable
{
    private final ExclusivePublication publication;
    private final Subscription subscription;
    private final AtomicBoolean running;
    private final MediaDriver mediaDriver;
    private final Aeron aeron;
    private final boolean ownsDriver;

    BasicPublisher(final AtomicBoolean running)
    {
        this(running, createEmbeddedMediaDriver(), aeronClient(), true);
    }

    BasicPublisher(
        final AtomicBoolean running, final MediaDriver mediaDriver, final Aeron aeron, final boolean ownsDriver)
    {
        this.running = running;
        this.mediaDriver = mediaDriver;
        this.aeron = aeron;
        this.ownsDriver = ownsDriver;

        publication = aeron.addExclusivePublication(receiverChannel(), receiverStreamId());

        subscription = aeron.addSubscription(senderChannel(), senderStreamId());

        while (!subscription.isConnected() || !publication.isConnected())
        {
            Thread.yield();
        }
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
                    checkPublicationResult(result);
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
                throw new IllegalStateException("image closed");
            }
        }
    }

    public void close()
    {
        closeAll(subscription, publication);

        if (ownsDriver)
        {
            closeAll(aeron, mediaDriver);
        }
    }

    public static void main(final String[] args)
    {
        SystemUtil.loadPropertiesFiles(args);

        final AtomicBoolean running = new AtomicBoolean(true);

        // Register a SIGINT handler for graceful shutdown.
        SigInt.register(() -> running.set(false));

        try (BasicPublisher server = new BasicPublisher(running))
        {
            server.run();
        }
    }
}
