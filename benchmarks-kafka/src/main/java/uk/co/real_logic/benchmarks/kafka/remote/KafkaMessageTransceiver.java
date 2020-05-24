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
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Callback;
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
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.time.Duration.ofMillis;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.agrona.BitUtil.SIZE_OF_LONG;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.kafka.remote.KafkaConfig.*;

abstract class KafkaMessageTransceiverProducerState extends MessageTransceiver
{
    final AtomicInteger outstandingRequests = new AtomicInteger();
    final AtomicReference<Throwable> error = new AtomicReference<>();
    final Callback sendCallback = (metadata, exception) ->
    {
        if (null != exception)
        {
            if (!error.compareAndSet(null, exception))
            {
                error.get().addSuppressed(exception);
            }
        }
        else
        {
            outstandingRequests.getAndDecrement();
        }
    };
    KafkaProducer<byte[], byte[]> producer;
    String topic;
    Integer partition;
    byte[] key;
    UnsafeBuffer sendBuffer;
    int maxInFlightMessages;

    KafkaMessageTransceiverProducerState(final MessageRecorder messageRecorder)
    {
        super(messageRecorder);
    }
}

@SuppressWarnings("unused")
abstract class KafkaMessageTransceiverProducerStatePadding extends KafkaMessageTransceiverProducerState
{
    byte p000, p001, p002, p003, p004, p005, p006, p007, p008, p009, p010, p011, p012, p013, p014, p015;
    byte p016, p017, p018, p019, p020, p021, p022, p023, p024, p025, p026, p027, p028, p029, p030, p031;
    byte p032, p033, p034, p035, p036, p037, p038, p039, p040, p041, p042, p043, p044, p045, p046, p047;
    byte p048, p049, p050, p051, p052, p053, p054, p055, p056, p057, p058, p059, p060, p061, p062, p063;

    KafkaMessageTransceiverProducerStatePadding(final MessageRecorder messageRecorder)
    {
        super(messageRecorder);
    }
}

public class KafkaMessageTransceiver extends KafkaMessageTransceiverProducerStatePadding
{
    private static final int NUM_PARTITIONS = 2;
    private static final short REPLICATION_FACTOR = (short)1;
    private static final Duration POLL_TIMEOUT = ofMillis(100);

    private KafkaConsumer<byte[], byte[]> consumer;
    private UnsafeBuffer receiverBuffer;

    public KafkaMessageTransceiver(final MessageRecorder messageRecorder)
    {
        super(messageRecorder);
    }

    public void init(final Configuration configuration) throws Exception
    {
        createTopic(configuration);
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
        sendBuffer = new UnsafeBuffer(new byte[payloadLength]);
        receiverBuffer = new UnsafeBuffer(new byte[payloadLength]);
    }

    private void createTopic(final Configuration configuration) throws Exception
    {
        final String outputFileNamePrefix = configuration.outputFileNamePrefix();
        topic = "benchmark-" + outputFileNamePrefix.substring(outputFileNamePrefix.lastIndexOf('_') + 1);

        final Properties config = new Properties();
        config.putAll(getCommonProperties());
        try (Admin admin = Admin.create(config))
        {
            final Set<String> topics = await(admin.listTopics().names());
            if (!topics.contains(topic))
            {
                final NewTopic newTopic = new NewTopic(topic, NUM_PARTITIONS, REPLICATION_FACTOR);
                newTopic.configs(getTopicConfig());
                await(admin.createTopics(singletonList(newTopic)).all());
            }
        }
    }

    private void initConsumer()
    {
        consumer = new KafkaConsumer<>(getConsumerConfig());

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
        maxInFlightMessages = getMaxInFlightMessages();
    }

    public void destroy() throws Exception
    {
        consumer.commitSync();
        closeAll(producer, consumer);
        final Throwable throwable = error.get();
        if (null != throwable)
        {
            LangUtil.rethrowUnchecked(throwable);
        }
    }

    public int send(final int numberOfMessages, final int messageLength, final long timestamp, final long checksum)
    {
        final AtomicInteger outstandingRequests = this.outstandingRequests;
        final int maxInFlightMessages = this.maxInFlightMessages;
        if (maxInFlightMessages == outstandingRequests.get())
        {
            return 0;
        }

        final byte[] messagePayload = createPayload(timestamp, checksum, messageLength);
        final String topic = this.topic;
        final Integer partition = this.partition;
        final byte[] key = this.key;
        final Callback callback = this.sendCallback;
        final KafkaProducer<byte[], byte[]> producer = this.producer;
        int sent = 0;
        for (int i = 0; i < numberOfMessages; i++)
        {
            if (maxInFlightMessages == outstandingRequests.getAndIncrement())
            {
                outstandingRequests.getAndDecrement();
                break;
            }
            final ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(
                topic,
                partition,
                key != null ? key.clone() : null,
                messagePayload.clone());
            producer.send(record, callback);
            sent++;
        }
        return sent;
    }

    private byte[] createPayload(final long timestamp, final long checksum, final int messageLength)
    {
        final UnsafeBuffer buffer = this.sendBuffer;
        buffer.putLong(0, timestamp, LITTLE_ENDIAN);
        buffer.putLong(messageLength - SIZE_OF_LONG, checksum, LITTLE_ENDIAN);
        return buffer.byteArray();
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
        for (final ConsumerRecord<byte[], byte[]> record : records)
        {
            final byte[] value = record.value();
            buffer.wrap(value);
            onMessageReceived(
                buffer.getLong(0, LITTLE_ENDIAN),
                buffer.getLong(value.length - SIZE_OF_LONG, LITTLE_ENDIAN));
        }
    }
}
