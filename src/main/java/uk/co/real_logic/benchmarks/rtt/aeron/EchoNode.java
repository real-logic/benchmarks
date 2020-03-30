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
import io.aeron.driver.MediaDriver;
import org.agrona.SystemUtil;
import org.agrona.concurrent.SigInt;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.Aeron.connect;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronUtil.*;

/**
 * Remote node which echoes original messages back to the sender.
 */
public final class EchoNode implements AutoCloseable
{
    private final ExclusivePublication publication;
    private final Subscription subscription;
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

        publication = aeron.addExclusivePublication(receiveChannel(), receiveStreamId());

        subscription = aeron.addSubscription(sendChannel(), sendStreamId());

        while (!subscription.isConnected() || !publication.isConnected())
        {
            Thread.yield();
        }
    }

    public void run()
    {
        publishLoop(publication, subscription, running);
    }

    public void close()
    {
        closeAll(subscription, publication);

        if (ownsAeronClient)
        {
            closeAll(aeron, mediaDriver);
            mediaDriver.context().deleteAeronDirectory();
        }
    }

    public static void main(final String[] args)
    {
        SystemUtil.loadPropertiesFiles(args);

        final AtomicBoolean running = new AtomicBoolean(true);

        // Register a SIGINT handler for graceful shutdown.
        SigInt.register(() -> running.set(false));

        try (EchoNode server = new EchoNode(running))
        {
            server.run();
        }
    }
}
