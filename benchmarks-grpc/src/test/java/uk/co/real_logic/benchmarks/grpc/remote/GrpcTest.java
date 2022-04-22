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

import org.HdrHistogram.Histogram;
import org.HdrHistogram.ValueRecorder;
import org.agrona.concurrent.NanoClock;
import org.agrona.concurrent.SystemNanoClock;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.co.real_logic.benchmarks.remote.*;

import java.io.PrintStream;

import static org.mockito.Mockito.mock;
import static uk.co.real_logic.benchmarks.grpc.remote.GrpcConfig.getServerBuilder;

abstract class GrpcTest
{
    @ParameterizedTest
    @Timeout(30)
    @ValueSource(classes = { BlockingMessageTransceiver.class, StreamingMessageTransceiver.class })
    void messageLength32bytes(final Class<MessageTransceiver> messageTransceiverClass) throws Exception
    {
        test(messageTransceiverClass, 10_000, 32, 10);
    }

    @ParameterizedTest
    @Timeout(30)
    @ValueSource(classes = { BlockingMessageTransceiver.class, StreamingMessageTransceiver.class })
    void messageLength1344bytes(final Class<MessageTransceiver> messageTransceiverClass) throws Exception
    {
        test(messageTransceiverClass, 100, 1344, 1);
    }

    @ParameterizedTest
    @Timeout(30)
    @ValueSource(classes = { BlockingMessageTransceiver.class, StreamingMessageTransceiver.class })
    void messageLength288bytes(final Class<MessageTransceiver> messageTransceiverClass) throws Exception
    {
        test(messageTransceiverClass, 1000, 288, 5);
    }

    protected abstract MessageTransceiver createMessageTransceiver(NanoClock nanoClock, ValueRecorder valueRecorder);

    private void test(
        final Class<MessageTransceiver> messageTransceiverClass,
        final int numberOfMessages,
        final int messageLength,
        final int burstSize) throws Exception
    {
        try (EchoServer server = new EchoServer(getServerBuilder()))
        {
            server.start();

            final NanoClock nanoClock = SystemNanoClock.INSTANCE;
            final PersistedHistogram persistedHistogram = new SinglePersistedHistogram(new Histogram(3));

            final Configuration configuration = new Configuration.Builder()
                .warmupIterations(0)
                .iterations(1)
                .messageRate(numberOfMessages)
                .batchSize(burstSize)
                .messageLength(messageLength)
                .messageTransceiverClass(messageTransceiverClass) // Not required, created directly
                .outputFileNamePrefix("grpc")
                .build();

            final LoadTestRig loadTestRig = new LoadTestRig(
                configuration,
                nanoClock,
                persistedHistogram,
                mock(PrintStream.class));

            loadTestRig.run();
        }
    }
}