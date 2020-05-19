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

import java.util.HashMap;
import java.util.Map;

import static java.lang.System.getProperty;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.agrona.SystemUtil.getDurationInNanos;
import static org.apache.kafka.clients.CommonClientConfigs.*;
import static uk.co.real_logic.benchmarks.kafka.remote.PartitionSelector.byName;

final class KafkaConfig
{
    public static final String KAFKA_SERVER_PROP_NAME = "uk.co.real_logic.benchmarks.kafka.remote.server";
    public static final String PARTITION_SELECTOR_PROP_NAME = "uk.co.real_logic.benchmarks.kafka.remote.partition";
    public static final String FLUSH_MESSAGES_PROP_NAME = "uk.co.real_logic.benchmarks.kafka.remote.flush.messages";
    public static final String FLUSH_TIME_PROP_NAME = "uk.co.real_logic.benchmarks.kafka.remote.flush.time";

    private KafkaConfig()
    {
    }

    public static String getServer()
    {
        final String brokerList = getProperty(KAFKA_SERVER_PROP_NAME, "").trim();
        if (brokerList.isEmpty())
        {
            throw new IllegalStateException("Kafka server was not configured, please set the '" +
                KAFKA_SERVER_PROP_NAME + "' system property");
        }
        return brokerList;
    }

    public static PartitionSelector getPartitionSelectorPropName()
    {
        return byName(getProperty(PARTITION_SELECTOR_PROP_NAME));
    }

    public static long getFlushMessagesInterval()
    {
        final String property = getProperty(FLUSH_MESSAGES_PROP_NAME);
        if (null != property)
        {
            return Long.parseLong(property);
        }
        return Long.MAX_VALUE;
    }

    public static long getFlushMessagesTimeMillis()
    {
        final long durationInNanos = getDurationInNanos(FLUSH_TIME_PROP_NAME, Long.MAX_VALUE);
        return Long.MAX_VALUE == durationInNanos ? durationInNanos : NANOSECONDS.toMillis(durationInNanos);
    }

    public static Map<String, String> getCommonProperties()
    {
        final Map<String, String> commonProps = new HashMap<>();
        commonProps.put(BOOTSTRAP_SERVERS_CONFIG, getServer());
        putIfProvided(commonProps, SEND_BUFFER_CONFIG);
        putIfProvided(commonProps, RECEIVE_BUFFER_CONFIG);
        return commonProps;
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
