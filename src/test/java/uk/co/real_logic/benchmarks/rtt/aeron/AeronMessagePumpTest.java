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

import org.agrona.LangUtil;
import org.agrona.collections.MutableInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.co.real_logic.benchmarks.rtt.Configuration;
import uk.co.real_logic.benchmarks.rtt.MessageRecorder;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.co.real_logic.benchmarks.rtt.aeron.MessagePumpConfiguration.EMBEDDED_MEDIA_DRIVER_PROP_NAME;

class AeronMessagePumpTest
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
    void pumpMessagesViaEchoPublisher() throws Exception
    {
        final int messages = 50_000;
        final Configuration configuration = new Configuration.Builder()
            .numberOfMessages(messages)
            .messagePumpClass(AeronMessagePump.class)
            .build();

        final MessagePumpDriver driver = new MessagePumpDriver();
        final AtomicBoolean running = new AtomicBoolean(true);
        final AtomicReference<Throwable> error = new AtomicReference<>();

        final Thread echoPublisher = new Thread(
            () ->
            {
                try (EchoPublisher publisher = new EchoPublisher(running, driver))
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

        final MutableInteger notifiedReceived = new MutableInteger();
        final AeronMessagePump messagePump = new AeronMessagePump(driver, new MessageRecorder()
        {
            public void record(final long timestamp)
            {
                notifiedReceived.increment();
            }

            public void reset()
            {
            }
        });

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
                            received += messagePump.receive();
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
            receiver.setName("receiver");
            receiver.setDaemon(true);
            receiver.start();

            int sent = 0;
            while ((sent += messagePump.send(messages - sent, configuration.messageLength(), 1_000_000_000)) < messages)
            {
            }

            receiver.join();
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
        assertEquals(messages, notifiedReceived.intValue());
    }
}