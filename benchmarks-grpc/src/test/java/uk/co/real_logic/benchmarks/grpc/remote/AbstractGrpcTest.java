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
package uk.co.real_logic.benchmarks.grpc.remote;

import org.agrona.collections.LongArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import uk.co.real_logic.benchmarks.remote.Configuration;
import uk.co.real_logic.benchmarks.remote.MessageRecorder;
import uk.co.real_logic.benchmarks.remote.MessageTransceiver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.co.real_logic.benchmarks.grpc.remote.GrpcConfig.getServerAddress;
import static uk.co.real_logic.benchmarks.remote.Configuration.MIN_MESSAGE_LENGTH;

abstract class AbstractGrpcTest
{
    @Timeout(30)
    @Test
    void messageLength16bytes() throws Exception
    {
        test(10_000, MIN_MESSAGE_LENGTH, 10);
    }

    @Timeout(30)
    @Test
    void messageLength200bytes() throws Exception
    {
        test(1000, 200, 5);
    }

    @Timeout(30)
    @Test
    void messageLength1KB() throws Exception
    {
        test(100, 1024, 1);
    }

    protected abstract MessageTransceiver createMessageTransceiver(MessageRecorder messageRecorder);

    private void test(
        final int numberOfMessages,
        final int messageLength,
        final int burstSize) throws Exception
    {
        try (EchoServer server = new EchoServer(getServerAddress()))
        {
            server.start();

            final LongArrayList sentTimestamps = new LongArrayList();
            final LongArrayList receivedTimestamps = new LongArrayList();
            final MessageTransceiver messageTransceiver = createMessageTransceiver(
                (timestamp, checksum) ->
                {
                    assertEquals(3 * timestamp, checksum);
                    receivedTimestamps.addLong(timestamp);
                });

            final Configuration configuration = new Configuration.Builder()
                .numberOfMessages(numberOfMessages)
                .messageLength(messageLength)
                .messageTransceiverClass(messageTransceiver.getClass())
                .build();

            messageTransceiver.init(configuration);
            try
            {

                int sent = 0;
                long timestamp = 1_000;
                while (sent < numberOfMessages || receivedTimestamps.size() < numberOfMessages)
                {
                    if (Thread.interrupted())
                    {
                        throw new IllegalStateException("run cancelled!");
                    }

                    if (sent < numberOfMessages)
                    {
                        int sentBatch = 0;
                        do
                        {
                            sentBatch += messageTransceiver.send(
                                burstSize - sentBatch, messageLength, timestamp, 3 * timestamp);
                            messageTransceiver.receive();
                        }
                        while (sentBatch < burstSize);

                        for (int i = 0; i < burstSize; i++)
                        {
                            sentTimestamps.add(timestamp);
                        }

                        sent += burstSize;
                        timestamp++;
                    }

                    if (receivedTimestamps.size() < numberOfMessages)
                    {
                        messageTransceiver.receive();
                    }
                }

                assertEquals(sentTimestamps, receivedTimestamps);
            }
            finally
            {
                messageTransceiver.destroy();
            }
        }
    }
}