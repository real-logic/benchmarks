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
package uk.co.real_logic.benchmarks.grpc.remote;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import uk.co.real_logic.benchmarks.remote.MessageRecorder;
import uk.co.real_logic.benchmarks.remote.MessageTransceiver;

import static java.lang.System.clearProperty;
import static java.lang.System.setProperty;
import static uk.co.real_logic.benchmarks.grpc.remote.GrpcConfig.TLS;

@DisabledOnOs(OS.WINDOWS)
class BlockingMessageTransceiverTest extends AbstractGrpcTest
{
    @BeforeEach
    void before()
    {
        setProperty(TLS, "true");
    }

    @AfterEach
    void after()
    {
        clearProperty(TLS);
    }

    protected MessageTransceiver createMessageTransceiver(final MessageRecorder messageRecorder)
    {
        return new BlockingMessageTransceiver(messageRecorder);
    }
}
