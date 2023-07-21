/*
 * Copyright 2023 Adaptive Financial Consulting Limited.
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.List;

import static uk.co.real_logic.benchmarks.aeron.remote.FailoverConstants.LEADER_STEP_DOWN_COMMAND;
import static uk.co.real_logic.benchmarks.aeron.remote.FailoverConstants.RESTART_COMMAND;

public final class FailoverControlClient implements AutoCloseable
{
    private final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(2048);
    private final InetSocketAddress[] targets;
    private final DatagramChannel channel;

    public FailoverControlClient(final List<InetSocketAddress> targets)
    {
        this.targets = targets.toArray(new InetSocketAddress[0]);
        if (this.targets.length == 0)
        {
            throw new IllegalArgumentException("no targets provided");
        }

        try
        {
            channel = createChannel();
        }
        catch (final IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private static DatagramChannel createChannel() throws IOException
    {
        final DatagramChannel channel = DatagramChannel.open();

        try
        {
            channel.configureBlocking(false);
        }
        catch (final IOException e)
        {
            try
            {
                channel.close();
            }
            catch (final IOException ce)
            {
                e.addSuppressed(ce);
            }

            throw e;
        }

        return channel;
    }

    public void sendStepDownCommand()
    {
        sendCommand(LEADER_STEP_DOWN_COMMAND);
    }

    public void sendRestartCommand()
    {
        sendCommand(RESTART_COMMAND);
    }

    private void sendCommand(final int command)
    {
        byteBuffer.clear();
        byteBuffer.putInt(command);
        byteBuffer.flip();

        for (final InetSocketAddress target : targets)
        {
            try
            {
                channel.send(byteBuffer, target);
            }
            catch (final IOException e)
            {
                throw new UncheckedIOException(e);
            }

            if (byteBuffer.hasRemaining())
            {
                throw new IllegalStateException("failed to send command");
            }

            byteBuffer.rewind();
        }
    }

    public void close() throws Exception
    {
        channel.close();
    }
}
