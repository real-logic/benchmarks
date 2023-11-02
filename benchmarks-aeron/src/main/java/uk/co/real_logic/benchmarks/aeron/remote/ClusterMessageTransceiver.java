/*
 * Copyright 2015-2023 Real Logic Limited.
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
import io.aeron.driver.MediaDriver;
import io.aeron.exceptions.AeronException;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.Header;
import org.HdrHistogram.ValueRecorder;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.NanoClock;
import uk.co.real_logic.benchmarks.remote.Configuration;
import uk.co.real_logic.benchmarks.remote.MessageTransceiver;

import java.nio.file.Path;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.*;

public class ClusterMessageTransceiver extends MessageTransceiver implements EgressListener
{
    private final BufferClaim bufferClaim = new BufferClaim();
    private final MediaDriver mediaDriver;
    private final AeronCluster.Context aeronClusterContext;
    private Path logsDir;
    private AeronCluster aeronCluster;

    public ClusterMessageTransceiver(final NanoClock nanoClock, final ValueRecorder valueRecorder)
    {
        this(nanoClock, valueRecorder, launchEmbeddedMediaDriverIfConfigured(), new AeronCluster.Context());
    }

    public ClusterMessageTransceiver(
        final NanoClock nanoClock,
        final ValueRecorder valueRecorder,
        final MediaDriver mediaDriver,
        final AeronCluster.Context aeronClusterContext)
    {
        super(nanoClock, valueRecorder);
        this.mediaDriver = mediaDriver;
        this.aeronClusterContext = aeronClusterContext.egressListener(this).clone();
    }

    public void init(final Configuration configuration) throws Exception
    {
        logsDir = configuration.logsDir();
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
    }

    public void destroy()
    {
        final String prefix = getClass().getSimpleName() + "-";
        AeronUtil.dumpAeronStats(
            aeronCluster.context().aeron().context().cncFile(),
            logsDir.resolve(prefix + "aeron-stat.txt"),
            logsDir.resolve(prefix + "errors.txt"));
        CloseHelper.closeAll(aeronCluster, mediaDriver);
    }

    public int send(final int numberOfMessages, final int messageLength, final long timestamp, final long checksum)
    {
        int count = 0;
        final AeronCluster aeronCluster = this.aeronCluster;
        final BufferClaim bufferClaim = this.bufferClaim;

        for (int i = 0; i < numberOfMessages; i++)
        {
            final long result = aeronCluster.tryClaim(messageLength, bufferClaim);
            if (result < 0)
            {
                checkPublicationResult(result);
                break;
            }

            final MutableDirectBuffer buffer = bufferClaim.buffer();
            final int msgOffset = bufferClaim.offset() + AeronCluster.SESSION_HEADER_LENGTH;
            buffer.putLong(msgOffset, timestamp, LITTLE_ENDIAN);
            buffer.putLong(msgOffset + messageLength - SIZE_OF_LONG, checksum, LITTLE_ENDIAN);
            bufferClaim.commit();
            count++;
        }

        return count;
    }

    public void receive()
    {
        aeronCluster.pollEgress();
    }

    public void onMessage(
        final long clusterSessionId,
        final long timestamp,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final Header header)
    {
        final long msgTimestamp = buffer.getLong(offset, LITTLE_ENDIAN);
        final long checksum = buffer.getLong(offset + length - SIZE_OF_LONG, LITTLE_ENDIAN);
        onMessageReceived(msgTimestamp, checksum);
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
}
