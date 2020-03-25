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

import static org.agrona.CloseHelper.closeAll;

final class MessagePumpDriver implements AutoCloseable
{
    private final MessagePumpConfiguration messagePumpConfiguration;
    private final MediaDriver mediaDriver;
    private final Aeron aeron;

    MessagePumpDriver()
    {
        this.messagePumpConfiguration = new MessagePumpConfiguration();
        if (messagePumpConfiguration.embeddedMediaDriver)
        {
            mediaDriver = MediaDriver.launch(new MediaDriver.Context().dirDeleteOnStart(true));
        }
        else
        {
            mediaDriver = null;
        }
        aeron = Aeron.connect();
    }

    MessagePumpConfiguration configuration()
    {
        return messagePumpConfiguration;
    }

    MediaDriver mediaDriver()
    {
        return mediaDriver;
    }

    Aeron aeron()
    {
        return aeron;
    }

    public void close()
    {
        closeAll(aeron, mediaDriver);
    }
}
