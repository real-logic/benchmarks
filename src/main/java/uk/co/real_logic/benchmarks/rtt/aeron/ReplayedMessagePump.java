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

import io.aeron.ChannelUri;
import io.aeron.Subscription;
import io.aeron.archive.client.RecordingDescriptorConsumer;
import org.agrona.collections.MutableLong;
import uk.co.real_logic.benchmarks.rtt.MessageRecorder;

import static java.lang.Long.MAX_VALUE;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronLauncher.*;

public final class ReplayedMessagePump extends BasicMessagePump
{
    public ReplayedMessagePump(final MessageRecorder messageRecorder)
    {
        this(new AeronLauncher(), messageRecorder);
    }

    ReplayedMessagePump(final AeronLauncher launcher, final MessageRecorder messageRecorder)
    {
        super(launcher, messageRecorder);
    }

    Subscription createSubscription()
    {
        final String receiverChannel = receiverChannel();
        final int receiverStreamId = receiverStreamId();
        final int replayStreamId = replayStreamId();

        final long recordingId = findLatestRecording(receiverChannel, receiverStreamId);

        final long sessionId = launcher.aeronArchive()
            .startReplay(recordingId, 0, MAX_VALUE, receiverChannel, replayStreamId);
        final String channel = ChannelUri.addSessionId(receiverChannel, (int)sessionId);

        return launcher.aeron().addSubscription(channel, replayStreamId);
    }

    private long findLatestRecording(final String recordingChannel, final int recordingStreamId)
    {
        final MutableLong lastRecordingId = new MutableLong();

        final RecordingDescriptorConsumer consumer =
            (controlSessionId,
            correlationId,
            recordingId,
            startTimestamp,
            stopTimestamp,
            startPosition,
            stopPosition,
            initialTermId,
            segmentFileLength,
            termBufferLength,
            mtuLength,
            sessionId,
            streamId,
            strippedChannel,
            originalChannel,
            sourceIdentity) -> lastRecordingId.set(recordingId);

        final int foundCount = launcher.aeronArchive()
            .listRecordingsForUri(0, 1, recordingChannel, recordingStreamId, consumer);

        if (foundCount == 0)
        {
            throw new IllegalStateException("no recordings found");
        }

        return lastRecordingId.get();
    }

}
