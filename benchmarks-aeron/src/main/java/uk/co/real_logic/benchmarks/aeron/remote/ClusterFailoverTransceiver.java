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

import io.aeron.Publication;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.cluster.codecs.EventCode;
import io.aeron.exceptions.AeronException;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.IdleStrategy;
import uk.co.real_logic.benchmarks.remote.Configuration;

import java.nio.file.Path;

import static io.aeron.cluster.client.AeronCluster.SESSION_HEADER_LENGTH;
import static java.util.Objects.requireNonNull;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.yieldUninterruptedly;
import static uk.co.real_logic.benchmarks.aeron.remote.FailoverConstants.*;

public final class ClusterFailoverTransceiver implements FailoverTransceiver, EgressListener
{
    private final BufferClaim bufferClaim = new BufferClaim();
    private final AeronCluster.Context aeronClusterContext;
    private AeronCluster aeronCluster;
    private IdleStrategy idleStrategy;
    private FailoverListener listener;
    private Path logsDir;

    public ClusterFailoverTransceiver()
    {
        this(new AeronCluster.Context());
    }

    public ClusterFailoverTransceiver(final AeronCluster.Context aeronClusterContext)
    {
        this.aeronClusterContext = aeronClusterContext.egressListener(this);
    }

    public void init(final Configuration configuration, final FailoverListener listener)
    {
        logsDir = configuration.logsDir();
        if (aeronCluster != null)
        {
            throw new IllegalStateException("Already initialised");
        }

        idleStrategy = configuration.idleStrategy();
        this.listener = requireNonNull(listener);

        aeronCluster = AeronCluster.connect(aeronClusterContext);

        while (true)
        {
            final Publication publication = aeronCluster.ingressPublication();
            if (null != publication && publication.isConnected())
            {
                break;
            }
            else
            {
                aeronCluster.pollEgress();
                yieldUninterruptedly();
            }
        }

        listener.onConnected(aeronCluster.clusterSessionId(), aeronCluster.leaderMemberId());
    }

    public int receive()
    {
        return aeronCluster.pollEgress();
    }

    public boolean trySendEcho(final int sequence, final long timestamp)
    {
        final AeronCluster aeronCluster = this.aeronCluster;
        final BufferClaim bufferClaim = this.bufferClaim;

        final long result = aeronCluster.tryClaim(ECHO_MESSAGE_LENGTH, bufferClaim);
        if (result > 0)
        {
            final MutableDirectBuffer buffer = bufferClaim.buffer();
            final int offset = bufferClaim.offset() + SESSION_HEADER_LENGTH;

            buffer.putInt(offset, ECHO_MESSAGE_TYPE);
            buffer.putInt(offset + ECHO_SEQUENCE_OFFSET, sequence);
            buffer.putLong(offset + ECHO_TIMESTAMP_OFFSET, timestamp);

            bufferClaim.commit();

            return true;
        }

        return false;
    }

    public void sendSync(final int expectedSequence)
    {
        final AeronCluster aeronCluster = this.aeronCluster;
        final BufferClaim bufferClaim = this.bufferClaim;
        final IdleStrategy idleStrategy = this.idleStrategy;

        idleStrategy.reset();

        while (true)
        {
            final long result = aeronCluster.tryClaim(SYNC_MESSAGE_LENGTH, bufferClaim);
            if (result > 0)
            {
                final MutableDirectBuffer buffer = bufferClaim.buffer();
                final int offset = bufferClaim.offset() + SESSION_HEADER_LENGTH;

                buffer.putInt(offset, SYNC_MESSAGE_TYPE);
                buffer.putInt(offset + SYNC_SEQUENCE_OFFSET, expectedSequence);

                bufferClaim.commit();

                break;
            }

            idleStrategy.idle();
        }
    }

    public void onMessage(
        final long clusterSessionId,
        final long clusterTimestamp,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final Header header)
    {
        final int messageType = buffer.getInt(offset);
        if (messageType == ECHO_MESSAGE_TYPE)
        {
            final int sequence = buffer.getInt(offset + ECHO_SEQUENCE_OFFSET);
            final long timestamp = buffer.getLong(offset + ECHO_TIMESTAMP_OFFSET);

            listener.onEchoMessage(sequence, timestamp);
        }
        else if (messageType == SYNC_MESSAGE_TYPE)
        {
            final int expectedSequence = buffer.getInt(offset + SYNC_SEQUENCE_OFFSET);

            listener.onSyncMessage(expectedSequence);
        }
    }

    public void onSessionEvent(
        final long correlationId,
        final long clusterSessionId,
        final long leadershipTermId,
        final int leaderMemberId,
        final EventCode code,
        final String detail)
    {
        if (code == EventCode.ERROR)
        {
            throw new AeronException("Error from Cluster: " + detail);
        }
    }

    public void onNewLeader(
        final long clusterSessionId,
        final long leadershipTermId,
        final int leaderMemberId,
        final String ingressEndpoints)
    {
        listener.onNewLeader(leaderMemberId);
    }

    public void close()
    {
        if (aeronCluster != null)
        {
            final String prefix = getClass().getSimpleName() + "-";
            AeronUtil.dumpAeronStats(
                aeronCluster.context().aeron().context().cncFile(),
                logsDir.resolve(prefix + "aeron-stat.txt"),
                logsDir.resolve(prefix + "errors.txt"));
            aeronCluster.close();
        }
    }
}
