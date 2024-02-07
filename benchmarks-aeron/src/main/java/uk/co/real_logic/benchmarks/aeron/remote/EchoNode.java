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
import io.aeron.ChannelUriStringBuilder;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.BlockHandler;
import io.aeron.protocol.HeaderFlyweight;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SystemNanoClock;
import org.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.benchmarks.remote.Configuration;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.aeron.Aeron.connect;
import static io.aeron.logbuffer.FrameDescriptor.*;
import static io.aeron.protocol.DataHeaderFlyweight.*;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.BitUtil.align;
import static org.agrona.CloseHelper.closeAll;
import static org.agrona.PropertyAction.PRESERVE;
import static org.agrona.PropertyAction.REPLACE;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;
import static uk.co.real_logic.benchmarks.util.PropertiesUtil.loadPropertiesFiles;
import static uk.co.real_logic.benchmarks.util.PropertiesUtil.mergeWithSystemProperties;

/**
 * Remote node which echoes original messages back to the sender.
 */
public final class EchoNode implements AutoCloseable, Runnable
{
    private final BlockHandler blockHandler;
    private final ExclusivePublication publication;
    private final Subscription subscription;
    private final AtomicBoolean running;
    private final MediaDriver mediaDriver;
    private final Aeron aeron;
    private final boolean ownsAeronClient;

    EchoNode(final AtomicBoolean running)
    {
        this(running, launchEmbeddedMediaDriverIfConfigured(), connect(), true, System.out);
    }

    EchoNode(
        final AtomicBoolean running,
        final MediaDriver mediaDriver,
        final Aeron aeron,
        final boolean ownsAeronClient,
        final PrintStream out)
    {
        this.running = running;
        this.mediaDriver = mediaDriver;
        this.aeron = aeron;
        this.ownsAeronClient = ownsAeronClient;

        subscription = aeron.addSubscription(destinationChannel(), destinationStreamId());
        awaitConnected(subscription::isConnected, connectionTimeoutNs(), SystemNanoClock.INSTANCE);

        // create a response channel that matches exactly the position of the request channel
        final Image image = subscription.imageAtIndex(0);
        final ChannelUriStringBuilder sourceChannel = new ChannelUriStringBuilder(sourceChannel())
            .initialPosition(image.position(), image.initialTermId(), image.termBufferLength())
            .mtu(image.mtuLength());

        publication = aeron.addExclusivePublication(sourceChannel.build(), sourceStreamId());
        awaitConnected(publication::isConnected, connectionTimeoutNs(), SystemNanoClock.INSTANCE);

        blockHandler = (buffer, offset, length, subSessionId, subTermId) ->
        {
            final UnsafeBuffer srcTermBuffer = (UnsafeBuffer)buffer;

            final int streamId = publication.streamId();
            final int sessionId = publication.sessionId();

            int currentOffset = offset, paddingFrameLength = 0;
            final int endOffset = offset + length;
            while (currentOffset < endOffset)
            {
                final int frameLength = frameLength(srcTermBuffer, currentOffset);
                final int frameType = frameType(srcTermBuffer, currentOffset);
                if (HeaderFlyweight.HDR_TYPE_DATA == frameType)
                {
                    final int alignedFrameLength = align(frameLength, FRAME_ALIGNMENT);

                    srcTermBuffer.putInt(currentOffset + SESSION_ID_FIELD_OFFSET, sessionId, LITTLE_ENDIAN);
                    srcTermBuffer.putInt(currentOffset + STREAM_ID_FIELD_OFFSET, streamId, LITTLE_ENDIAN);

                    currentOffset += alignedFrameLength;
                }
                else if (HeaderFlyweight.HDR_TYPE_PAD == frameType)
                {
                    paddingFrameLength = frameLength;
                    break;
                }
            }

            if (0 == paddingFrameLength)
            {
                long result;
                while ((result = publication.offerBlock(srcTermBuffer, offset, length)) <= 0)
                {
                    checkPublicationResult(result);
                }
            }
            else
            {
                long result;
                while ((result = publication.appendPadding(paddingFrameLength - HEADER_LENGTH)) <= 0)
                {
                    checkPublicationResult(result);
                }
            }
        };
    }

    public void run()
    {
        final IdleStrategy idleStrategy = idleStrategy();

        final AtomicBoolean running = this.running;

        final Image image = subscription.imageAtIndex(0);
        while (true)
        {
            final int availableWindow = (int)publication.availableWindow();
            if (availableWindow > 0)
            {
                final int remainingTermLength = publication.termBufferLength() - publication.termOffset();
                final int fragments = image.blockPoll(
                    blockHandler,
                    0 == remainingTermLength ? availableWindow : Math.min(availableWindow, remainingTermLength));
                if (0 == fragments)
                {
                    if (!running.get())
                    {
                        return; // Abort execution
                    }

                    if (image.isClosed())
                    {
                        return;  // Abort execution
                    }
                }
                idleStrategy.idle(fragments);
            }
            else
            {
                idleStrategy.idle();
            }
        }
    }

    public void close()
    {
        closeAll(subscription);
        closeAll(publication);

        if (ownsAeronClient)
        {
            closeAll(aeron, mediaDriver);
        }
    }

    public static void main(final String[] args)
    {
        Thread.currentThread().setName("echo");
        mergeWithSystemProperties(PRESERVE, loadPropertiesFiles(new Properties(), REPLACE, args));
        final Path outputDir = Configuration.resolveLogsDir();

        final AtomicBoolean running = new AtomicBoolean(true);
        installSignalHandler(() -> running.set(false));

        try (EchoNode server = new EchoNode(running))
        {
            server.run();

            final String prefix = "echo-server-";
            AeronUtil.dumpAeronStats(
                server.aeron.context().cncFile(),
                outputDir.resolve(prefix + "aeron-stat.txt"),
                outputDir.resolve(prefix + "errors.txt"));
        }
    }
}
