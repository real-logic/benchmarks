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

import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2LongCounterMap;
import org.agrona.concurrent.IdleStrategy;

import static io.aeron.cluster.client.AeronCluster.SESSION_HEADER_LENGTH;
import static uk.co.real_logic.benchmarks.aeron.remote.AeronUtil.checkPublicationResult;
import static uk.co.real_logic.benchmarks.aeron.remote.FailoverConstants.*;

public final class FailoverClusteredService implements ClusteredService
{
    private final Long2LongCounterMap expectedSequenceBySessionId = new Long2LongCounterMap(-1);
    private final BufferClaim bufferClaim = new BufferClaim();
    private final FailoverManager failoverManager;
    private Cluster cluster;

    public FailoverClusteredService(final FailoverManager failoverManager)
    {
        this.failoverManager = failoverManager;
    }

    public void onStart(final Cluster cluster, final Image snapshotImage)
    {
        this.cluster = cluster;
    }

    public void onSessionOpen(final ClientSession session, final long timestamp)
    {
        expectedSequenceBySessionId.put(session.id(), 0);
    }

    public void onSessionClose(final ClientSession session, final long timestamp, final CloseReason closeReason)
    {
        expectedSequenceBySessionId.remove(session.id());
    }

    public void onSessionMessage(
        final ClientSession session,
        final long timestamp,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final Header header)
    {
        final int messageType = buffer.getInt(offset);
        if (messageType == ECHO_MESSAGE_TYPE)
        {
            onEchoMessage(session, buffer, offset, length);
        }
        else if (messageType == SYNC_MESSAGE_TYPE)
        {
            onSyncMessage(session, buffer, offset);
        }
    }

    private void onEchoMessage(
        final ClientSession session,
        final DirectBuffer buffer,
        final int offset,
        final int length)
    {
        final int flags = buffer.getInt(offset + ECHO_FLAGS_OFFSET);
        final int sequence = buffer.getInt(offset + ECHO_SEQUENCE_OFFSET);

        final long expected = expectedSequenceBySessionId.getAndIncrement(session.id());
        if (sequence != expected)
        {
            throw new IllegalStateException("expected sequence " + expected + ", but got " + sequence +
                " from session " + session.id());
        }

        if (flags == LEADER_STEP_DOWN_FLAG && cluster.role() == Cluster.Role.LEADER)
        {
            failoverManager.stepDown();
        }

        final IdleStrategy idleStrategy = cluster.idleStrategy();
        final BufferClaim bufferClaim = this.bufferClaim;

        idleStrategy.reset();
        long result;
        while ((result = session.tryClaim(length, bufferClaim)) <= 0)
        {
            checkPublicationResult(result);
            idleStrategy.idle();
        }

        final MutableDirectBuffer dstBuffer = bufferClaim.buffer();
        final int dstOffset = bufferClaim.offset() + SESSION_HEADER_LENGTH;
        dstBuffer.putBytes(dstOffset, buffer, offset, length);
        bufferClaim.commit();
    }

    private void onSyncMessage(final ClientSession session, final DirectBuffer buffer, final int offset)
    {
        final int clientExpectedSequence = buffer.getInt(offset + SYNC_SEQUENCE_OFFSET);
        final long sessionId = session.id();
        final long clusterExpectedSequence = expectedSequenceBySessionId.get(sessionId);

        System.out.println("Syncing session " + sessionId + ": clientExpectedSequence=" + clientExpectedSequence +
            " clusterExpectedSequence=" + clusterExpectedSequence);

        if (clientExpectedSequence < clusterExpectedSequence)
        {
            // cluster sent some echo responses which got lost
            expectedSequenceBySessionId.put(sessionId, clientExpectedSequence);
        }
        else if (clientExpectedSequence > clusterExpectedSequence)
        {
            throw new IllegalStateException("Client has seen future messages");
        }

        final IdleStrategy idleStrategy = cluster.idleStrategy();
        final BufferClaim bufferClaim = this.bufferClaim;

        idleStrategy.reset();
        long result;
        while ((result = session.tryClaim(SYNC_MESSAGE_LENGTH, bufferClaim)) <= 0)
        {
            checkPublicationResult(result);
            idleStrategy.idle();
        }

        final MutableDirectBuffer dstBuffer = bufferClaim.buffer();
        final int dstOffset = bufferClaim.offset() + SESSION_HEADER_LENGTH;

        dstBuffer.putInt(dstOffset, SYNC_MESSAGE_TYPE);
        dstBuffer.putInt(dstOffset + SYNC_SEQUENCE_OFFSET, clientExpectedSequence);

        bufferClaim.commit();
    }

    public void onTimerEvent(final long correlationId, final long timestamp)
    {
    }

    public void onTakeSnapshot(final ExclusivePublication snapshotPublication)
    {
    }

    public void onRoleChange(final Cluster.Role newRole)
    {
    }

    public void onTerminate(final Cluster cluster)
    {
    }
}
