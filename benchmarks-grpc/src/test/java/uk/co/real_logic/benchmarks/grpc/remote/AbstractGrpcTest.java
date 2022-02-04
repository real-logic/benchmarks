/*
 * Copyright 2015-2022 Real Logic Limited.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import uk.co.real_logic.benchmarks.remote.Configuration;
import uk.co.real_logic.benchmarks.remote.LoadTestRig;
import uk.co.real_logic.benchmarks.remote.MessageTransceiver;

import java.io.PrintStream;

import static org.mockito.Mockito.mock;
import static uk.co.real_logic.benchmarks.grpc.remote.GrpcConfig.getServerBuilder;

abstract class AbstractGrpcTest
{
    @Timeout(30)
    @Test
    void messageLength32bytes() throws Exception
    {
        test(10_000, 32, 10);
    }

    @Timeout(30)
    @Test
    void messageLength288bytes() throws Exception
    {
        test(1000, 288, 5);
    }

    @Timeout(30)
    @Test
    void messageLength1344bytes() throws Exception
    {
        test(100, 1344, 1);
    }

    protected abstract MessageTransceiver createMessageTransceiver();

    private void test(
        final int numberOfMessages,
        final int messageLength,
        final int burstSize) throws Exception
    {
        try (EchoServer server = new EchoServer(getServerBuilder()))
        {
            server.start();

            final MessageTransceiver messageTransceiver = createMessageTransceiver();

            final Configuration configuration = new Configuration.Builder()
                .warmupIterations(0)
                .iterations(1)
                .messageRate(numberOfMessages)
                .batchSize(burstSize)
                .messageLength(messageLength)
                .messageTransceiverClass(messageTransceiver.getClass())
                .outputFileNamePrefix("grpc")
                .build();

            final LoadTestRig loadTestRig = new LoadTestRig(configuration, messageTransceiver, mock(PrintStream.class));
            loadTestRig.run();
        }
    }
}