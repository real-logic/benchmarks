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

import org.agrona.LangUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import uk.co.real_logic.benchmarks.remote.Configuration;
import uk.co.real_logic.benchmarks.remote.MessageRecorder;
import uk.co.real_logic.benchmarks.remote.MessageTransceiver;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.time.Duration.ofMillis;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.kafka.remote.KafkaConfig.*;

public class KafkaMessageTransceiver extends MessageTransceiver
{
    private static final int NUM_PARTITIONS = 2;
    private static final short REPLICATION_FACTOR = (short)1;
    private static final AtomicInteger GROUP_ID = new AtomicInteger(1);
    private static final AtomicInteger TOPIC_ID = new AtomicInteger(5000);
    private static final Duration POLL_TIMEOUT = ofMillis(100);

    private KafkaConsumer<byte[], byte[]> consumer;
    private KafkaProducer<byte[], byte[]> producer;
    private String topic;
    private Integer partition;
    private byte[] key;
    private int checksumOffset;
    private UnsafeBuffer sendBuffer;
    private UnsafeBuffer receiverBuffer;

    public KafkaMessageTransceiver(final MessageRecorder messageRecorder)
    {
        super(messageRecorder);
    }

    public void init(final Configuration configuration) throws Exception
    {
        createTopic();
        initConsumer();
        initProducer();

        partition = null;
        key = null;
        final PartitionSelector partitionSelector = KafkaConfig.getPartitionSelector();
        switch (partitionSelector)
        {
            case EXPLICIT:
                partition = 0;
                break;
            case BY_KEY:
                final byte[] bytes = new byte[32];
                ThreadLocalRandom.current().nextBytes(bytes);
                key = bytes;
                break;
        }
        final int payloadLength = configuration.messageLength();
        checksumOffset = payloadLength - SIZE_OF_LONG;
        sendBuffer = new UnsafeBuffer(new byte[payloadLength]);
        receiverBuffer = new UnsafeBuffer(new byte[payloadLength]);
    }

    private void createTopic()
    {
        topic = "topic-" + TOPIC_ID.getAndAdd(1);

        final Properties config = new Properties();
        config.putAll(getCommonProperties());
        try (Admin admin = Admin.create(config))
        {
            final Set<String> topics = await(admin.listTopics().names());
            if (topics.contains(topic))
            {
                await(admin.deleteTopics(singletonList(topic)).all());
            }
            final NewTopic newTopic = new NewTopic(topic, NUM_PARTITIONS, REPLICATION_FACTOR);
            newTopic.configs(getTopicConfig());
            await(admin.createTopics(singletonList(newTopic)).all());
        }
    }

    private void initConsumer()
    {
        final Properties config = getConsumerConfig();
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "consumer-group-" + GROUP_ID.getAndAdd(1));
        consumer = new KafkaConsumer<>(config);

        final List<TopicPartition> topicPartitions = consumer.partitionsFor(topic).stream()
            .map(partitionInfo -> new TopicPartition(partitionInfo.topic(), partitionInfo.partition()))
            .collect(toList());
        consumer.assign(topicPartitions);
        consumer.seekToEnd(topicPartitions);
        consumer.assignment().forEach(topicPartition -> consumer.position(topicPartition));
    }

    private void initProducer()
    {
        producer = new KafkaProducer<>(getProducerConfig());
    }

    public void destroy() throws Exception
    {
        consumer.commitSync();
        closeAll(producer, consumer);
    }

    public int send(final int numberOfMessages, final int messageLength, final long timestamp, final long checksum)
    {
        final byte[] value = createPayload(timestamp, checksum);
        sendMessages(numberOfMessages, value);
        return numberOfMessages;
    }

    private byte[] createPayload(final long timestamp, final long checksum)
    {
        final UnsafeBuffer buffer = this.sendBuffer;
        final int checksumOffset = this.checksumOffset;
        buffer.putLong(0, timestamp, LITTLE_ENDIAN);
        buffer.putLong(checksumOffset, checksum, LITTLE_ENDIAN);
        if (checksumOffset > SIZE_OF_LONG)
        {
            buffer.setMemory(SIZE_OF_LONG, checksumOffset - SIZE_OF_LONG, (byte)(checksum ^ timestamp));
        }

        return buffer.byteArray().clone();
    }

    private void sendMessages(
        final int numberOfMessages,
        final byte[] value)
    {
        final String topic = this.topic;
        final Integer partition = this.partition;
        final byte[] key = this.key;
        final KafkaProducer<byte[], byte[]> producer = this.producer;
        for (int i = 0; i < numberOfMessages; i++)
        {
            producer.send(new ProducerRecord<>(topic, partition, key, value), null);
        }
    }

    private static <T> T await(final Future<? extends T> future)
    {
        try
        {
            return future.get();
        }
        catch (final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            LangUtil.rethrowUnchecked(e);
            return null;
        }
        catch (final ExecutionException e)
        {
            LangUtil.rethrowUnchecked(e);
            return null;
        }
    }

    public void receive()
    {
        final ConsumerRecords<byte[], byte[]> records = consumer.poll(POLL_TIMEOUT);
        if (records.isEmpty())
        {
            return;
        }

        final UnsafeBuffer buffer = this.receiverBuffer;
        final int checksumOffset = this.checksumOffset;
        for (final ConsumerRecord<byte[], byte[]> record : records)
        {
            buffer.wrap(record.value());
            onMessageReceived(buffer.getLong(0, LITTLE_ENDIAN), buffer.getLong(checksumOffset, LITTLE_ENDIAN));
        }
    }
}
