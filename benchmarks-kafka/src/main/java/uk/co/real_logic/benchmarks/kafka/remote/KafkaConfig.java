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

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static java.lang.System.getProperty;
import static org.agrona.Strings.isEmpty;
import static org.apache.kafka.clients.CommonClientConfigs.*;
import static uk.co.real_logic.benchmarks.kafka.remote.PartitionSelector.byName;

final class KafkaConfig
{
    public static final String PARTITION_SELECTOR_PROP_NAME = "uk.co.real_logic.benchmarks.kafka.remote.partition";

    private KafkaConfig()
    {
    }

    public static PartitionSelector getPartitionSelector()
    {
        return byName(getProperty(PARTITION_SELECTOR_PROP_NAME));
    }

    public static Map<String, String> getCommonProperties()
    {
        final Map<String, String> commonProps = new HashMap<>();
        commonProps.put(BOOTSTRAP_SERVERS_CONFIG, getBrokerList());
        putIfProvided(commonProps, SEND_BUFFER_CONFIG);
        putIfProvided(commonProps, RECEIVE_BUFFER_CONFIG);
        return commonProps;
    }

    public static Map<String, String> getTopicConfig()
    {
        final Map<String, String> topicConfig = new HashMap<>();
        topicConfig.put(TopicConfig.COMPRESSION_TYPE_CONFIG, "producer");
        final String maxLong = Long.toString(Long.MAX_VALUE);
        topicConfig.put(TopicConfig.FLUSH_MESSAGES_INTERVAL_CONFIG,
            getProperty(TopicConfig.FLUSH_MESSAGES_INTERVAL_CONFIG, maxLong));
        topicConfig.put(TopicConfig.FLUSH_MS_CONFIG,
            getProperty(TopicConfig.FLUSH_MS_CONFIG, maxLong));
        return topicConfig;
    }

    public static Properties getConsumerConfig()
    {
        final Properties config = new Properties();
        config.putAll(getCommonProperties());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
            getProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"));
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"));
        config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG,
            getProperty(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "0")); //ensure we have no temporal batching
        config.put(ConsumerConfig.CHECK_CRCS_CONFIG,
            getProperty(ConsumerConfig.CHECK_CRCS_CONFIG, "false"));
        config.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG,
            getProperty(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, String.valueOf(1024 * 1024 * 1024)));
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
            getProperty(ProducerConfig.MAX_BLOCK_MS_CONFIG, Long.toString(Long.MAX_VALUE)));
        config.put(ProducerConfig.ACKS_CONFIG,
            getProperty(ProducerConfig.ACKS_CONFIG, "0"));
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG,
            getProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none"));
        return config;
    }

    private static String getBrokerList()
    {
        final String brokerList = getProperty(BOOTSTRAP_SERVERS_CONFIG);
        if (isEmpty(brokerList))
        {
            throw new IllegalStateException("broker list is empty, please set the '" + BOOTSTRAP_SERVERS_CONFIG +
                "' system property");
        }
        return brokerList;
    }

    private static void putIfProvided(final Map<String, String> commonProps, final String sendBufferConfig)
    {
        final String property = getProperty(sendBufferConfig);
        if (null != property)
        {
            commonProps.put(sendBufferConfig, property);
        }
    }
}
