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

import io.aeron.driver.MediaDriver;
import org.agrona.LangUtil;
import org.agrona.collections.LongArrayList;
import org.junit.jupiter.api.Test;
import uk.co.real_logic.benchmarks.rtt.Configuration;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class BasicPublisherTest
{
    @Test
    @SuppressWarnings("MethodLength")
    void test() throws Exception
    {
        final int messages = 1_000_000;
        final Configuration configuration = new Configuration.Builder()
            .numberOfMessages(messages)
            .messagePumpClass(BasicMessagePump.class)
            .build();

        final AeronLauncher launcher = new AeronLauncher(MediaDriver.class);
        final AtomicBoolean running = new AtomicBoolean(true);
        final AtomicReference<Throwable> error = new AtomicReference<>();

        final Thread echoPublisher = new Thread(
            () ->
            {
                try (BasicPublisher publisher = new BasicPublisher(running, launcher, false))
                {
                    publisher.run();
                }
                catch (final Throwable t)
                {
                    if (!error.compareAndSet(null, t))
                    {
                        error.get().addSuppressed(t);
                    }
                }
            });
        echoPublisher.setName("echo-publisher");
        echoPublisher.setDaemon(true);
        echoPublisher.start();

        final LongArrayList timestamps = new LongArrayList(messages, Long.MIN_VALUE);
        final BasicMessagePump messagePump = new BasicMessagePump(launcher, timestamp -> timestamps.addLong(timestamp));

        messagePump.init(configuration);
        try
        {
            final Thread receiver = new Thread(
                () ->
                {
                    int received = 0;
                    while (received < messages)
                    {
                        try
                        {
                            final int count = messagePump.receive();
                            if (0 == count && null != error.get())
                            {
                                LangUtil.rethrowUnchecked(error.get());
                            }
                            received += count;
                        }
                        catch (final Throwable t)
                        {
                            if (!error.compareAndSet(null, t))
                            {
                                error.get().addSuppressed(t);
                            }
                            break;
                        }
                    }
                });
            receiver.setName("message-receiver");
            receiver.setDaemon(true);
            receiver.start();

            try
            {
                Thread.currentThread().setName("message-sender");
                int sent = 0;
                long timestamp = 1_000;
                while (sent < messages)
                {
                    if (messagePump.send(1, configuration.messageLength(), timestamp) == 1)
                    {
                        sent++;
                        timestamp++;
                    }
                    else if (null != error.get())
                    {
                        LangUtil.rethrowUnchecked(error.get());
                    }
                }
            }
            finally
            {
                receiver.join();
            }
        }
        finally
        {
            running.set(false);
            echoPublisher.join();
            messagePump.destroy();
        }

        if (null != error.get())
        {
            LangUtil.rethrowUnchecked(error.get());
        }
        assertArrayEquals(LongStream.range(1_000, 1_000 + messages).toArray(), timestamps.toLongArray());
    }
}