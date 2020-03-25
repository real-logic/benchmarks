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

import org.junit.jupiter.api.Test;

import static java.lang.String.valueOf;
import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static uk.co.real_logic.benchmarks.rtt.aeron.MessagePumpConfiguration.*;

class MessagePumpConfigurationTest
{
    @Test
    void defaultValues()
    {
        final MessagePumpConfiguration configuration = new MessagePumpConfiguration();

        assertEquals("aeron:udp?endpoint=localhost:33333", configuration.senderChannel);
        assertEquals(101010, configuration.senderStreamId);
        assertEquals("aeron:udp?endpoint=localhost:33334", configuration.receiverChannel);
        assertEquals(101011, configuration.receiverStreamId);
        assertFalse(configuration.embeddedMediaDriver);
        assertEquals(10, configuration.frameCountLimit);
    }

    @Test
    void explicitValues()
    {
        final String senderChannel = "sender";
        final int senderStreamId = Integer.MIN_VALUE;
        final String receiverChannel = "receiver";
        final int receiverStreamId = Integer.MAX_VALUE;
        final boolean embeddedMediaDriver = true;
        final int frameCountLimit = 111;

        setProperty(SENDER_CHANNEL_PROP_NAME, senderChannel);
        setProperty(SENDER_STREAM_ID_PROP_NAME, valueOf(senderStreamId));
        setProperty(RECEIVER_CHANNEL_PROP_NAME, receiverChannel);
        setProperty(RECEIVER_STREAM_ID_PROP_NAME, valueOf(receiverStreamId));
        setProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME, valueOf(embeddedMediaDriver));
        setProperty(FRAME_COUNT_LIMIT_PROP_NAME, valueOf(frameCountLimit));
        try
        {
            final MessagePumpConfiguration configuration = new MessagePumpConfiguration();

            assertEquals(senderChannel, configuration.senderChannel);
            assertEquals(senderStreamId, configuration.senderStreamId);
            assertEquals(receiverChannel, configuration.receiverChannel);
            assertEquals(receiverStreamId, configuration.receiverStreamId);
            assertEquals(embeddedMediaDriver, configuration.embeddedMediaDriver);
            assertEquals(frameCountLimit, configuration.frameCountLimit);
        }
        finally
        {
            clearProperty(SENDER_CHANNEL_PROP_NAME);
            clearProperty(SENDER_STREAM_ID_PROP_NAME);
            clearProperty(RECEIVER_CHANNEL_PROP_NAME);
            clearProperty(RECEIVER_STREAM_ID_PROP_NAME);
            clearProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME);
            clearProperty(FRAME_COUNT_LIMIT_PROP_NAME);
        }
    }
}