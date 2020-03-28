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

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import org.agrona.LangUtil;
import org.agrona.collections.LongArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.co.real_logic.benchmarks.rtt.Configuration;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;

import static io.aeron.Aeron.connect;
import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static org.agrona.CloseHelper.closeAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronUtil.EMBEDDED_MEDIA_DRIVER_PROP_NAME;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronUtil.launchEmbeddedMediaDriverIfConfigured;

class BasicPublisherTest
{
    @BeforeAll
    static void before()
    {
        setProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME, "true");
    }

    @AfterAll
    static void after()
    {
        clearProperty(EMBEDDED_MEDIA_DRIVER_PROP_NAME);
    }

    @Test
    void test() throws Exception
    {
        final int messages = 1_000_000;
        final Configuration configuration = new Configuration.Builder()
            .numberOfMessages(messages)
            .messagePumpClass(BasicMessagePump.class)
            .build();

        final MediaDriver mediaDriver = launchEmbeddedMediaDriverIfConfigured();
        final Aeron aeron = connect();
        final AtomicBoolean running = new AtomicBoolean(true);
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final CountDownLatch publisherStarted = new CountDownLatch(1);

        final Thread echoPublisher = new Thread(
            () ->
            {
                publisherStarted.countDown();

                try (BasicPublisher publisher = new BasicPublisher(running, mediaDriver, aeron, false))
                {
                    publisher.run();
                }
                catch (final Throwable t)
                {
                    error.set(t);
                }
            });
        echoPublisher.setName("basic-publisher");
        echoPublisher.setDaemon(true);
        echoPublisher.start();

        final LongArrayList timestamps = new LongArrayList(messages, Long.MIN_VALUE);
        final BasicMessagePump messagePump = new BasicMessagePump(
            mediaDriver,
            aeron,
            false,
            timestamp -> timestamps.addLong(timestamp));

        publisherStarted.await();

        messagePump.init(configuration);
        try
        {
            Thread.currentThread().setName("basic-message-pump");
            int sent = 0;
            int received = 0;
            long timestamp = 1_000;
            while (sent < messages || received < messages)
            {
                if (sent < messages && messagePump.send(1, configuration.messageLength(), timestamp) == 1)
                {
                    sent++;
                    timestamp++;
                }
                if (received < messages)
                {
                    received += messagePump.receive();
                }
                if (null != error.get())
                {
                    LangUtil.rethrowUnchecked(error.get());
                }
            }
        }
        finally
        {
            running.set(false);
            echoPublisher.join();
            messagePump.destroy();
            closeAll(aeron, mediaDriver);
        }

        if (null != error.get())
        {
            LangUtil.rethrowUnchecked(error.get());
        }
        assertArrayEquals(LongStream.range(1_000, 1_000 + messages).toArray(), timestamps.toLongArray());
    }
}