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
package uk.co.real_logic.benchmarks.kafka.remote;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.metrics.Sensor;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static java.lang.System.getProperty;
import static org.agrona.Strings.isEmpty;
import static org.apache.kafka.clients.CommonClientConfigs.*;
import static uk.co.real_logic.benchmarks.kafka.remote.PartitionSelection.byName;

final class KafkaConfig
{
    public static final String PARTITION_SELECTION_PROP_NAME = "uk.co.real_logic.benchmarks.kafka.remote.partition";
    public static final String PRODUCER_MAX_IN_FLIGHT_MESSAGES_PROP_NAME =
        "uk.co.real_logic.benchmarks.kafka.remote.producer.max.in.flight.messages";

    private KafkaConfig()
    {
    }

    public static PartitionSelection getPartitionSelection()
    {
        return byName(getProperty(PARTITION_SELECTION_PROP_NAME));
    }

    public static Map<String, String> getCommonProperties()
    {
        final Map<String, String> commonProps = new HashMap<>();

        commonProps.put(BOOTSTRAP_SERVERS_CONFIG, getBrokerList());
        putIfProvided(commonProps, SECURITY_PROTOCOL_CONFIG);
        putIfProvided(commonProps, SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG);
        putIfProvided(commonProps, SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG);
        putIfProvided(commonProps, SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG);
        putIfProvided(commonProps, SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG);
        putIfProvided(commonProps, SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG);
        putIfProvided(commonProps, SEND_BUFFER_CONFIG);
        putIfProvided(commonProps, RECEIVE_BUFFER_CONFIG);
        putIfProvided(commonProps, REQUEST_TIMEOUT_MS_CONFIG);
        putIfProvided(commonProps, DEFAULT_API_TIMEOUT_MS_CONFIG);
        commonProps.put(METRICS_SAMPLE_WINDOW_MS_CONFIG, getProperty(METRICS_SAMPLE_WINDOW_MS_CONFIG, "30000"));
        commonProps.put(METRICS_NUM_SAMPLES_CONFIG, getProperty(METRICS_NUM_SAMPLES_CONFIG, "2"));
        commonProps.put(
            METRICS_RECORDING_LEVEL_CONFIG,
            getProperty(METRICS_RECORDING_LEVEL_CONFIG, Sensor.RecordingLevel.INFO.toString()));
        commonProps.put(METRIC_REPORTER_CLASSES_CONFIG, getProperty(METRIC_REPORTER_CLASSES_CONFIG, ""));

        return commonProps;
    }

    public static Map<String, String> getTopicConfig()
    {
        final Map<String, String> topicConfig = new HashMap<>();

        topicConfig.put(TopicConfig.COMPRESSION_TYPE_CONFIG, "producer");
        topicConfig.put(TopicConfig.PREALLOCATE_CONFIG, getProperty(TopicConfig.PREALLOCATE_CONFIG, "false"));
        putIfProvided(topicConfig, TopicConfig.FLUSH_MESSAGES_INTERVAL_CONFIG);
        putIfProvided(topicConfig, TopicConfig.FLUSH_MS_CONFIG);

        return topicConfig;
    }

    public static Properties getConsumerConfig()
    {
        final Properties config = new Properties();

        config.putAll(getCommonProperties());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "benchmark-consumer");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
            getProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"));
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"));
        config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG,
            getProperty(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "0")); //ensure we have no temporal batching
        config.put(ConsumerConfig.CHECK_CRCS_CONFIG, getProperty(ConsumerConfig.CHECK_CRCS_CONFIG, "false"));

        return config;
    }

    public static Properties getProducerConfig()
    {
        final Properties config = new Properties();

        config.putAll(getCommonProperties());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        config.put(ProducerConfig.LINGER_MS_CONFIG,
            getProperty(ProducerConfig.LINGER_MS_CONFIG, "0")); //ensure writes are synchronous
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG,
            getProperty(ProducerConfig.MAX_BLOCK_MS_CONFIG, "300000"));
        config.put(ProducerConfig.ACKS_CONFIG,
            getProperty(ProducerConfig.ACKS_CONFIG, "0"));
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG,
            getProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none"));
        config.put(ProducerConfig.BATCH_SIZE_CONFIG,
            getProperty(ProducerConfig.BATCH_SIZE_CONFIG, Long.toString(16 * 1024)));
        config.put(ProducerConfig.BUFFER_MEMORY_CONFIG,
            getProperty(ProducerConfig.BUFFER_MEMORY_CONFIG, Long.toString(32 * 1024 * 1024)));
        config.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG,
            getProperty(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, Long.toString(1024 * 1024)));

        return config;
    }

    public static int getMaxInFlightMessages()
    {
        return Integer.getInteger(PRODUCER_MAX_IN_FLIGHT_MESSAGES_PROP_NAME, 1000);
    }

    private static String getBrokerList()
    {
        final String brokerList = getProperty(BOOTSTRAP_SERVERS_CONFIG);
        if (isEmpty(brokerList))
        {
            throw new IllegalStateException(
                "broker list is empty, please set the '" + BOOTSTRAP_SERVERS_CONFIG + "' system property");
        }

        return brokerList;
    }

    private static void putIfProvided(final Map<String, String> props, final String propName)
    {
        final String value = getProperty(propName);
        if (null != value)
        {
            props.put(propName, value);
        }
    }
}
