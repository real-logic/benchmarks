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

import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.YieldingIdleStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
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
            .messagePumpClass(SampleMessagePump.class)
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
    void throwsIllegalArgumentExceptionIfBatchSizeIsInvalid(final int size)
    {
        final Builder builder = new Builder()
            .numberOfMessages(1000)
            .batchSize(size);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("Batch size cannot be less than 1, got: " + size, ex.getMessage());
    }

    @ParameterizedTest
    @MethodSource("messageSizes")
    void throwsIllegalArgumentExceptionIfMessageLengthIsLessThanMinimumSize(final int length)
    {
        final Builder builder = new Builder()
            .numberOfMessages(200)
            .messageLength(length);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("Message length cannot be less than " + MIN_MESSAGE_LENGTH + ", got: " + length,
            ex.getMessage());
    }

    @Test
    void throwsNullPointerExceptionIfMessagePumpClassIsNull()
    {
        final Builder builder = new Builder()
            .numberOfMessages(10);

        final NullPointerException ex =
            assertThrows(NullPointerException.class, () -> builder.build());

        assertEquals("MessagePump class cannot be null", ex.getMessage());
    }

    @Test
    void throwsIllegalArgumentExceptionIfMessagePumpClassIsAnInterface()
    {
        final Builder builder = new Builder()
            .numberOfMessages(10)
            .messagePumpClass(TestMessagePump.class);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("MessagePump class must be a concrete class", ex.getMessage());
    }

    @Test
    void throwsIllegalArgumentExceptionIfMessagePumpClassIsAnAbstractClass()
    {
        final Builder builder = new Builder()
            .numberOfMessages(10)
            .messagePumpClass(TestAbstractMessagePump.class);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("MessagePump class must be a concrete class", ex.getMessage());
    }

    @Test
    void throwsIllegalArgumentExceptionIfMessagePumpClassHasNoPublicConstructor()
    {
        final Builder builder = new Builder()
            .numberOfMessages(10)
            .messagePumpClass(TestNoPublicConstructorMessagePump.class);

        final IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> builder.build());

        assertEquals("MessagePump class must have a public no-arg constructor", ex.getMessage());
    }

    @Test
    void throwsNullPointerExceptionIfSenderIdleStrategyIsNull()
    {
        final Builder builder = new Builder()
            .numberOfMessages(4)
            .messagePumpClass(SampleMessagePump.class)
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
            .messagePumpClass(SampleMessagePump.class)
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
            .messagePumpClass(SampleMessagePump.class)
            .build();

        assertEquals(123, configuration.numberOfMessages());
        assertEquals(DEFAULT_WARM_UP_NUMBER_OF_MESSAGES, configuration.warmUpNumberOfMessages());
        assertEquals(DEFAULT_WARM_UP_ITERATIONS, configuration.warmUpIterations());
        assertEquals(DEFAULT_ITERATIONS, configuration.iterations());
        assertEquals(DEFAULT_BATCH_SIZE, configuration.batchSize());
        assertEquals(MIN_MESSAGE_LENGTH, configuration.messageLength());
        assertSame(SampleMessagePump.class, configuration.messagePumpClass());
        assertSame(NoOpIdleStrategy.INSTANCE, configuration.senderIdleStrategy());
        assertSame(NoOpIdleStrategy.INSTANCE, configuration.receiverIdleStrategy());
    }

    @Test
    void explicitOptions()
    {
        final Configuration configuration = new Builder()
            .warmUpNumberOfMessages(222)
            .warmUpIterations(3)
            .iterations(11)
            .numberOfMessages(666)
            .batchSize(4)
            .messageLength(119)
            .messagePumpClass(SampleMessagePump.class)
            .senderIdleStrategy(NoOpIdleStrategy.INSTANCE)
            .receiverIdleStrategy(YieldingIdleStrategy.INSTANCE)
            .build();

        assertEquals(3, configuration.warmUpIterations());
        assertEquals(222, configuration.warmUpNumberOfMessages());
        assertEquals(11, configuration.iterations());
        assertEquals(666, configuration.numberOfMessages());
        assertEquals(4, configuration.batchSize());
        assertEquals(119, configuration.messageLength());
        assertSame(SampleMessagePump.class, configuration.messagePumpClass());
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
            .batchSize(2)
            .messageLength(64)
            .messagePumpClass(SampleMessagePump.class)
            .senderIdleStrategy(NoOpIdleStrategy.INSTANCE)
            .receiverIdleStrategy(YieldingIdleStrategy.INSTANCE)
            .build();

        assertEquals("warmUpIterations=4, warmUpNumberOfMessages=3, iterations=10, numberOfMessages=777, " +
            "batchSize=2, messageLength=64, " +
            "messagePumpClass=uk.co.real_logic.benchmarks.rtt.SampleMessagePump, " +
            "senderIdleStrategy=NoOpIdleStrategy{}, receiverIdleStrategy=YieldingIdleStrategy{}",
            configuration.toString());
    }

    @Test
    void fromPropertiesThrowsNullPointerExceptionIfPropertiesMapIsNull()
    {
        assertThrows(NullPointerException.class, () -> fromProperties(null));
    }

    @Test
    void fromPropertiesThrowsIllegalArgumentExceptionIfNumberOfMessagesIsNotConfigured()
    {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> fromProperties(emptyMap()));

        assertEquals("Property '" + MESSAGES_PROP_NAME + "' is required!", ex.getMessage());
    }

    @Test
    void fromPropertiesThrowsIllegalArgumentExceptionIfNumberOfMessagesHasInvalidValue()
    {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> fromProperties(singletonMap(MESSAGES_PROP_NAME, "100x000")));

        assertEquals("Non-integer value for property '" + MESSAGES_PROP_NAME +
            "', cause: 'x' is not a valid digit @ 3", ex.getMessage());
    }

    @Test
    void fromPropertiesThrowsIllegalArgumentExceptionIfMessagePumpPropertyIsNotConfigured()
    {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> fromProperties(singletonMap(MESSAGES_PROP_NAME, "100")));

        assertEquals("Property '" + MESSAGE_PUMP_PROP_NAME + "' is required!", ex.getMessage());
    }

    @Test
    void fromPropertiesThrowsIllegalArgumentExceptionIfMessagePumpHasInvalidValue()
    {
        final Map<String, String> properties = new HashMap<>();
        properties.put(MESSAGES_PROP_NAME, "20");
        properties.put(MESSAGE_PUMP_PROP_NAME, Integer.class.getName());

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> fromProperties(properties));

        assertEquals("Invalid class value for property '" + MESSAGE_PUMP_PROP_NAME +
            "', cause: class java.lang.Integer", ex.getMessage());
    }

    @Test
    void fromPropertiesDefaults()
    {
        final Map<String, String> properties = new HashMap<>();
        properties.put(MESSAGES_PROP_NAME, "42");
        properties.put(MESSAGE_PUMP_PROP_NAME, SampleMessagePump.class.getName());

        final Configuration configuration = fromProperties(properties);

        assertEquals(42, configuration.numberOfMessages());
        assertEquals(DEFAULT_WARM_UP_NUMBER_OF_MESSAGES, configuration.warmUpNumberOfMessages());
        assertEquals(DEFAULT_WARM_UP_ITERATIONS, configuration.warmUpIterations());
        assertEquals(DEFAULT_ITERATIONS, configuration.iterations());
        assertEquals(DEFAULT_BATCH_SIZE, configuration.batchSize());
        assertEquals(MIN_MESSAGE_LENGTH, configuration.messageLength());
        assertSame(SampleMessagePump.class, configuration.messagePumpClass());
        assertSame(NoOpIdleStrategy.INSTANCE, configuration.senderIdleStrategy());
        assertSame(NoOpIdleStrategy.INSTANCE, configuration.receiverIdleStrategy());
    }

    @Test
    void fromPropertiesOverrideAll()
    {
        final Map<String, String> properties = new HashMap<>();
        properties.put(WARM_UP_ITERATIONS_PROP_NAME, "2");
        properties.put(WARM_UP_MESSAGES_PROP_NAME, "10");
        properties.put(ITERATIONS_PROP_NAME, "4");
        properties.put(MESSAGES_PROP_NAME, "200");
        properties.put(BATCH_SIZE_PROP_NAME, "3");
        properties.put(MESSAGE_LENGTH_PROP_NAME, "24");
        properties.put(MESSAGE_PUMP_PROP_NAME, SampleMessagePump.class.getName());
        properties.put(SENDER_IDLE_STRATEGY_PROP_NAME, YieldingIdleStrategy.class.getName());
        properties.put(RECEIVER_IDLE_STRATEGY_PROP_NAME, NoOpIdleStrategy.class.getName());

        final Configuration configuration = fromProperties(properties);

        assertEquals(2, configuration.warmUpIterations());
        assertEquals(10, configuration.warmUpNumberOfMessages());
        assertEquals(4, configuration.iterations());
        assertEquals(200, configuration.numberOfMessages());
        assertEquals(3, configuration.batchSize());
        assertEquals(24, configuration.messageLength());
        assertSame(SampleMessagePump.class, configuration.messagePumpClass());
        assertTrue(YieldingIdleStrategy.class.isInstance(configuration.senderIdleStrategy()));
        assertTrue(NoOpIdleStrategy.class.isInstance(configuration.receiverIdleStrategy()));
    }

    private static IntStream messageSizes()
    {
        return IntStream.range(-1, MIN_MESSAGE_LENGTH);
    }

    private interface TestMessagePump extends MessagePump
    {
    }

    private abstract class TestAbstractMessagePump implements MessagePump
    {
    }

    private final class TestNoPublicConstructorMessagePump implements MessagePump
    {
        private TestNoPublicConstructorMessagePump()
        {
        }

        public void init(final Configuration configuration) throws Exception
        {
        }

        public void destroy() throws Exception
        {
        }

        public Sender sender()
        {
            return null;
        }

        public Receiver receiver()
        {
            return null;
        }
    }
}