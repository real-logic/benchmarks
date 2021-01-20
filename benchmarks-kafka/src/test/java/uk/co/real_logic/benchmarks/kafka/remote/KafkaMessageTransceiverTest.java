/*
 * Copyright 2015-2021 Real Logic Limited.
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
import org.apache.kafka.common.config.SslConfigs;
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
import static java.util.Arrays.sort;
import static org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.co.real_logic.benchmarks.kafka.remote.KafkaConfig.PARTITION_SELECTION_PROP_NAME;

class KafkaMessageTransceiverTest
{
    private static KafkaEmbeddedCluster cluster;

    @BeforeAll
    static void beforeAll(final @TempDir Path tempDir) throws Exception
    {
        cluster = new KafkaEmbeddedCluster(13500, 13501, 13502, tempDir);
    }

    @AfterAll
    static void afterAll(final @TempDir Path tempDir)
    {
        cluster.close();
    }

    @Timeout(30)
    @ParameterizedTest
    @EnumSource(PartitionSelection.class)
    void messageLength32bytes(final PartitionSelection partitionSelection) throws Exception
    {
        setProperty(BOOTSTRAP_SERVERS_CONFIG, "localhost:13501");
        setProperty(PARTITION_SELECTION_PROP_NAME, partitionSelection.name());
        try
        {
            test(500, 32, 10);
        }
        finally
        {
            clearProperty(BOOTSTRAP_SERVERS_CONFIG);
            clearProperty(PARTITION_SELECTION_PROP_NAME);
        }
    }

    @Timeout(30)
    @Test
    void messageLength1376bytes() throws Exception
    {
        setProperty(BOOTSTRAP_SERVERS_CONFIG, "localhost:13502");
        setProperty(SECURITY_PROTOCOL_CONFIG, "SSL");
        final Path certificatesPath = Configuration.certificatesDirectory();
        setProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG,
            certificatesPath.resolve("truststore.p12").toString());
        setProperty(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PKCS12");
        setProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, "truststore");
        setProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG,
            certificatesPath.resolve("client.keystore").toString());
        setProperty(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12");
        setProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, "client");

        try
        {
            test(50, 1376, 1);
        }
        finally
        {
            clearProperty(BOOTSTRAP_SERVERS_CONFIG);
            clearProperty(SECURITY_PROTOCOL_CONFIG);
            clearProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG);
            clearProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG);
            clearProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG);
            clearProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG);
        }
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
            .messageRate(numberOfMessages)
            .messageLength(messageLength)
            .messageTransceiverClass(messageTransceiver.getClass())
            .outputFileNamePrefix("kafka")
            .build();

        messageTransceiver.init(configuration);

        try
        {
            int count = 0;
            long timestamp = 12345;
            while (count < numberOfMessages || receivedTimestamps.size() < numberOfMessages)
            {
                if (Thread.interrupted())
                {
                    throw new IllegalStateException("run cancelled!");
                }

                if (count < numberOfMessages)
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

                    count += burstSize;
                    timestamp++;
                }

                if (receivedTimestamps.size() < numberOfMessages)
                {
                    messageTransceiver.receive();
                }
            }

            final long[] sent = sentTimestamps.toLongArray();
            final long[] received = receivedTimestamps.toLongArray();

            sort(received);
            assertArrayEquals(sent, received);
        }
        finally
        {
            messageTransceiver.destroy();
        }
    }
}