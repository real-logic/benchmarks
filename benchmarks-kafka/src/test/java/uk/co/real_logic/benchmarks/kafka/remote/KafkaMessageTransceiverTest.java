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
package uk.co.real_logic.benchmarks.kafka.remote;

import org.agrona.collections.LongArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uk.co.real_logic.benchmarks.remote.Configuration;
import uk.co.real_logic.benchmarks.remote.MessageTransceiver;

import java.nio.file.Path;

import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.co.real_logic.benchmarks.kafka.remote.KafkaConfig.KAFKA_SERVER_PROP_NAME;
import static uk.co.real_logic.benchmarks.kafka.remote.KafkaConfig.PARTITION_SELECTOR_PROP_NAME;
import static uk.co.real_logic.benchmarks.remote.Configuration.MIN_MESSAGE_LENGTH;

class KafkaMessageTransceiverTest
{
    private static KafkaEmbeddedCluster cluster;

    @BeforeAll
    static void beforeAll(final @TempDir Path tempDir) throws Exception
    {
        cluster = new KafkaEmbeddedCluster(13500, 13501, tempDir);
        setProperty(KAFKA_SERVER_PROP_NAME, cluster.brokerList());
    }

    @AfterAll
    static void afterAll(final @TempDir Path tempDir) throws Exception
    {
        clearProperty(KAFKA_SERVER_PROP_NAME);
        cluster.close();
    }

    @Timeout(30)
    @ParameterizedTest
    @EnumSource(PartitionSelector.class)
    void messageLength16bytes(final PartitionSelector partitionSelector) throws Exception
    {
        setProperty(PARTITION_SELECTOR_PROP_NAME, partitionSelector.name());
        try
        {
            test(500, MIN_MESSAGE_LENGTH, 10);
        }
        finally
        {
            clearProperty(PARTITION_SELECTOR_PROP_NAME);
        }
    }

    @Timeout(30)
    @Test
    void messageLength1KB() throws Exception
    {
        test(50, 1024, 1);
    }

    private void test(
        final int numberOfMessages,
        final int messageLength,
        final int burstSize) throws Exception
    {
        final LongArrayList sentTimestamps = new LongArrayList();
        final LongArrayList receivedTimestamps = new LongArrayList();
        final MessageTransceiver messageTransceiver = new KafkaMessageTransceiver(
            (timestamp, checksum) ->
            {
                assertEquals(timestamp / 5, checksum);
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
            long timestamp = System.nanoTime();
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
                            burstSize - sentBatch, messageLength, timestamp, timestamp / 5);
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