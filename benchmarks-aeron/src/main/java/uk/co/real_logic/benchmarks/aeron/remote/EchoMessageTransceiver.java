/*
 * Copyright 2015-2024 Real Logic Limited.
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
import io.aeron.FragmentAssembler;
import io.aeron.Image;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.BufferClaim;
import org.HdrHistogram.ValueRecorder;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.SystemNanoClock;
import uk.co.real_logic.benchmarks.remote.Configuration;
import uk.co.real_logic.benchmarks.remote.MessageTransceiver;

import java.nio.file.Path;

import static io.aeron.Aeron.connect;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

public final class EchoMessageTransceiver extends MessageTransceiver
{
    private final BufferClaim bufferClaim = new BufferClaim();
    private final FragmentAssembler dataHandler = new FragmentAssembler(
        (buffer, offset, length, header) ->
        {
            final long timestamp = buffer.getLong(offset, LITTLE_ENDIAN);
            final long checksum = buffer.getLong(offset + length - SIZE_OF_LONG, LITTLE_ENDIAN);
            onMessageReceived(timestamp, checksum);
        });

    private final MediaDriver mediaDriver;
    private final Aeron aeron;
    private final boolean ownsAeronClient;
    private Path logsDir;
    ExclusivePublication publication;
    private Subscription subscription;
    private Image image;

    public EchoMessageTransceiver(final NanoClock nanoClock, final ValueRecorder valueRecorder)
    {
        this(nanoClock, valueRecorder, launchEmbeddedMediaDriverIfConfigured(), connect(), true);
    }

    EchoMessageTransceiver(
        final NanoClock nanoClock,
        final ValueRecorder valueRecorder,
        final MediaDriver mediaDriver,
        final Aeron aeron,
        final boolean ownsAeronClient)
    {
        super(nanoClock, valueRecorder);
        this.mediaDriver = mediaDriver;
        this.aeron = aeron;
        this.ownsAeronClient = ownsAeronClient;
    }

    public void init(final Configuration configuration)
    {
        logsDir = configuration.logsDir();
        final int streamId = destinationStreamId();
        publication = aeron.addExclusivePublication(destinationChannel(), streamId);
        subscription = aeron.addSubscription(sourceChannel(), streamId);

        awaitConnected(
            () -> subscription.isConnected() && publication.isConnected(),
            connectionTimeoutNs(),
            SystemNanoClock.INSTANCE);

        image = subscription.imageAtIndex(0);
    }

    public void destroy()
    {
        final String prefix = "echo-client-";
        AeronUtil.dumpAeronStats(
            aeron.context().cncFile(),
            logsDir.resolve(prefix + "aeron-stat.txt"),
            logsDir.resolve(prefix + "errors.txt"));
        closeAll(subscription, publication);

        if (ownsAeronClient)
        {
            closeAll(aeron, mediaDriver);
        }
    }

    public int send(final int numberOfMessages, final int messageLength, final long timestamp, final long checksum)
    {
        return sendMessages(publication, bufferClaim, numberOfMessages, messageLength, timestamp, checksum);
    }

    public void receive()
    {
        image.poll(dataHandler, FRAGMENT_LIMIT);
    }
}
