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

/**
 * Configuration used for the {@link AeronMessagePump} and {@link EchoPublisher}.
 */
final class MessagePumpConfiguration
{
    /**
     * Name of the system property that specifies channel for the sender.
     */
    public static final String SENDER_CHANNEL_PROP_NAME = "aeron.benchmarks.rtt.aeron.sender.channel";
    public static final String SENDER_STREAM_ID_PROP_NAME = "aeron.benchmarks.rtt.aeron.sender.streamId";
    /**
     * Name of the system property that specifies channel for the receiver.
     */
    public static final String RECEIVER_CHANNEL_PROP_NAME = "aeron.benchmarks.rtt.aeron.receiver.channel";
    public static final String RECEIVER_STREAM_ID_PROP_NAME = "aeron.benchmarks.rtt.aeron.receiver.streamId";

    public static final String EMBEDDED_MEDIA_DRIVER_PROP_NAME = "aeron.benchmarks.rtt.aeron.embeddedMediaDriver";
    public static final String FRAME_COUNT_LIMIT_PROP_NAME = "aeron.benchmarks.rtt.aeron.frameCountLimit";

    final String senderChannel;
    final String receiverChannel;
    final int senderStreamId;
    final int receiverStreamId;
    final int frameCountLimit;
    final boolean embeddedMediaDriver;

    MessagePumpConfiguration()
    {
        senderChannel = System.getProperty(SENDER_CHANNEL_PROP_NAME, "aeron:udp?endpoint=localhost:33333");
        receiverChannel = System.getProperty(RECEIVER_CHANNEL_PROP_NAME, "aeron:udp?endpoint=localhost:33334");
        senderStreamId = Integer.getInteger(SENDER_STREAM_ID_PROP_NAME, 101010);
        receiverStreamId = Integer.getInteger(RECEIVER_STREAM_ID_PROP_NAME, 101011);
        embeddedMediaDriver = Boolean.getBoolean(EMBEDDED_MEDIA_DRIVER_PROP_NAME);
        frameCountLimit = Integer.getInteger(FRAME_COUNT_LIMIT_PROP_NAME, 10);
    }
}
