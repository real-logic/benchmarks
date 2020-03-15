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
package uk.co.real_logic.benchmarks.rtt;

import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static uk.co.real_logic.benchmarks.rtt.Configuration.*;

class ConfigurationTest
{
    @Test
    void throwsIllegalArgumentExceptionIfWarmUpIterationsIsANegativeNumber()
    {
        final Builder builder = new Builder()
            .warmUpIterations(-1);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("Warm-up iterations cannot be less than 0, got: -1", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = { -6, 0 })
    void throwsIllegalArgumentExceptionIfWarmUpNumberOfMessagesIsInvalid(final int warmUpNumberOfMessages)
    {
        final Builder builder = new Builder()
            .warmUpIterations(3)
            .warmUpNumberOfMessages(warmUpNumberOfMessages);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("Warm-up number of messages cannot be less than 1, got: " + warmUpNumberOfMessages, ex
            .getMessage());
    }

    @Test
    void warmUpNumberOfMessagesCanBeZeroIfWarmUpIterationsIsZero()
    {
        final Configuration configuration = new Builder()
            .warmUpIterations(0)
            .warmUpNumberOfMessages(0)
            .numberOfMessages(1_000)
            .build();

        assertEquals(0, configuration.warmUpIterations());
        assertEquals(0, configuration.warmUpNumberOfMessages());
    }

    @ParameterizedTest
    @ValueSource(ints = { -200, 0 })
    void throwsIllegalArgumentExceptionIfIterationsIsInvalid(final int iterations)
    {
        final Builder builder = new Builder()
            .iterations(iterations);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("Iterations cannot be less than 1, got: " + iterations,
            ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = { -123, 0 })
    void throwsIllegalArgumentExceptionIfNumberOfMessagesIsInvalid(final int numberOfMessages)
    {
        final Builder builder = new Builder()
            .numberOfMessages(numberOfMessages);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("Number of messages cannot be less than 1, got: " + numberOfMessages, ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = { Integer.MIN_VALUE, 0 })
    void throwsIllegalArgumentExceptionIfBurstSizeIsInvalid(final int burstSize)
    {
        final Builder builder = new Builder()
            .numberOfMessages(1000)
            .burstSize(burstSize);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("Burst size cannot be less than 1, got: " + burstSize, ex.getMessage());
    }

    @ParameterizedTest
    @MethodSource("messageSizes")
    void throwsIllegalArgumentExceptionIfMessageSizeIsLessThanMinimumSize(final int messageSize)
    {
        final Builder builder = new Builder()
            .numberOfMessages(200)
            .messageSize(messageSize);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("Message size cannot be less than " + MIN_MESSAGE_SIZE + ", got: " + messageSize, ex.getMessage());
    }

    @Test
    void throwsNullPointerExceptionIfSenderIdleStrategyIsNull()
    {
        final Builder builder = new Builder()
            .numberOfMessages(4)
            .senderIdleStrategy(null);

        final NullPointerException ex =
            assertThrows(NullPointerException.class, () -> builder.build());

        assertEquals("Sender IdleStrategy cannot be null", ex.getMessage());
    }

    @Test
    void throwsNullPointerExceptionIfReceiverIdleStrategyIsNull()
    {
        final Builder builder = new Builder()
            .numberOfMessages(4)
            .receiverIdleStrategy(null);

        final NullPointerException ex =
            assertThrows(NullPointerException.class, () -> builder.build());

        assertEquals("Receiver IdleStrategy cannot be null", ex.getMessage());
    }

    @Test
    void defaultOptions()
    {
        final Configuration configuration = new Builder()
            .numberOfMessages(123)
            .build();

        assertEquals(123, configuration.numberOfMessages());
        assertEquals(DEFAULT_WARM_UP_NUMBER_OF_MESSAGES, configuration.warmUpNumberOfMessages());
        assertEquals(DEFAULT_WARM_UP_ITERATIONS, configuration.warmUpIterations());
        assertEquals(DEFAULT_ITERATIONS, configuration.iterations());
        assertEquals(DEFAULT_BURST_SIZE, configuration.burstSize());
        assertEquals(MIN_MESSAGE_SIZE, configuration.messageSize());
        assertSame(BusySpinIdleStrategy.INSTANCE, configuration.senderIdleStrategy());
        assertSame(BusySpinIdleStrategy.INSTANCE, configuration.receiverIdleStrategy());
    }

    @Test
    void explicitOptions()
    {
        final Configuration configuration = new Builder()
            .warmUpNumberOfMessages(222)
            .warmUpIterations(3)
            .iterations(11)
            .numberOfMessages(666)
            .burstSize(4)
            .messageSize(119)
            .senderIdleStrategy(NoOpIdleStrategy.INSTANCE)
            .receiverIdleStrategy(YieldingIdleStrategy.INSTANCE)
            .build();

        assertEquals(3, configuration.warmUpIterations());
        assertEquals(222, configuration.warmUpNumberOfMessages());
        assertEquals(11, configuration.iterations());
        assertEquals(666, configuration.numberOfMessages());
        assertEquals(4, configuration.burstSize());
        assertEquals(119, configuration.messageSize());
        assertSame(NoOpIdleStrategy.INSTANCE, configuration.senderIdleStrategy());
        assertSame(YieldingIdleStrategy.INSTANCE, configuration.receiverIdleStrategy());
    }

    @Test
    void toStringPrintsConfiguredValues()
    {
        final Configuration configuration = new Builder()
            .warmUpIterations(4)
            .warmUpNumberOfMessages(3)
            .iterations(10)
            .numberOfMessages(777)
            .burstSize(2)
            .messageSize(64)
            .senderIdleStrategy(NoOpIdleStrategy.INSTANCE)
            .receiverIdleStrategy(YieldingIdleStrategy.INSTANCE)
            .build();

        assertEquals("warmUpIterations=4, warmUpNumberOfMessages=3, iterations=10, numberOfMessages=777, " +
                "burstSize=2, messageSize=64, senderIdleStrategy=NoOpIdleStrategy{}, " +
                "receiverIdleStrategy=YieldingIdleStrategy{}",
            configuration.toString());
    }

    private static IntStream messageSizes()
    {
        return IntStream.range(-1, MIN_MESSAGE_SIZE);
    }

}