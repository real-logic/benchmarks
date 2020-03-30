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
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import uk.co.real_logic.benchmarks.rtt.MessageRecorder;

import static io.aeron.Aeron.connect;
import static org.agrona.CloseHelper.closeAll;
import static uk.co.real_logic.benchmarks.rtt.aeron.AeronUtil.*;

public final class PlainMessageTransceiver extends AbstractMessageTransceiver
{
    private final MediaDriver mediaDriver;
    private final Aeron aeron;
    private final boolean ownsAeronClient;

    public PlainMessageTransceiver(final MessageRecorder messageRecorder)
    {
        this(launchEmbeddedMediaDriverIfConfigured(), connect(), true, messageRecorder);
    }

    PlainMessageTransceiver(
        final MediaDriver mediaDriver,
        final Aeron aeron,
        final boolean ownsAeronClient,
        final MessageRecorder messageRecorder)
    {
        super(messageRecorder);
        this.mediaDriver = mediaDriver;
        this.aeron = aeron;
        this.ownsAeronClient = ownsAeronClient;
    }

    protected ExclusivePublication createPublication()
    {
        return aeron.addExclusivePublication(sendChannel(), sendStreamId());
    }

    protected Subscription createSubscription()
    {
        return aeron.addSubscription(receiveChannel(), receiveStreamId());
    }

    public void destroy() throws Exception
    {
        super.destroy();

        if (ownsAeronClient)
        {
            closeAll(aeron, mediaDriver);
            mediaDriver.context().deleteAeronDirectory();
        }
    }

}
